package com.wt.airconditioner;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Наблюдатели обоих сидений идут по фактическим уровням, термостат считает
 * решение. ModelHeatDriver применяет его только к сиденьям в режиме «Авто».
 * В ручном режиме и пресетах оценка продолжает идти для следующего запуска.
 *
 * Ни одной ссылки на Android: время, воздух и уровни подаёт сервис, снимок
 * хранится через Store. Дизайн —
 * docs/superpowers/specs/2026-09-14-model-shadow-mode-design.md.
 */
final class ModelShadow {

    /** Где лежат снимки сидений между перезапусками процесса. */
    interface Store {
        /** null — снимка нет. */
        String load(Seat seat);

        /**
         * Оба сиденья разом: снимок пишется на каждом обновлении, а посидельная
         * запись означала бы два полных перезаписывания файла настроек подряд.
         */
        void save(Map<Seat, String> snapshots);
    }

    /** Обычный выбор уровня — раз в минуту, раздел 5 спецификации модели. */
    private static final long DECISION_INTERVAL_MS = 60_000L;

    /** Расчёт дольше этого попадает в лог: на голове он идёт в главном потоке. */
    private static final long SLOW_UPDATE_MS = 100L;

    /** Отдельная причина решения при прогнозе перегрева — для лога и ряда. */
    private static final String COAST_REASON = "перегрев в прогнозе, ноль без ожидания минуты";

    private final Store store;
    private final LogSink logSink;

    /** null — воздух ещё ни разу не был достоверен. */
    private Map<Seat, SeatObserver> observers;
    private boolean disabled;

    private final Map<Seat, SeatThermostat.Decision> decisions = new EnumMap<>(Seat.class);
    private final Map<Seat, Boolean> coasting = new EnumMap<>(Seat.class);
    /** Последний увиденный уровень и когда он сменился; нет ключа — не сменялся. */
    private final Map<Seat, Integer> seenLevels = new EnumMap<>(Seat.class);
    private final Map<Seat, Long> levelChangedAtMs = new EnumMap<>(Seat.class);
    private final Map<Seat, Long> lastDecisionAtMs = new EnumMap<>(Seat.class);

    ModelShadow(Store store, LogSink logSink) {
        this.store = store;
        this.logSink = logSink;
    }

    /** Новый запуск авто должен получить свежий план, а не решение из фона. */
    void requestDecision(Seat seat) {
        lastDecisionAtMs.remove(seat);
    }

    /**
     * Обновление на тиках автоматики/ряда и событиях уровня и зажигания.
     * Сбой отключает расчёт до перезапуска процесса: estimate возвращает null,
     * а ModelHeatDriver требует ноль. Пресеты и ручное управление продолжаются.
     *
     * @param levels подтверждённые автомобилем уровни; нет ключа — не известен
     * @param air показания воздуха со временем получения; достоверность решает
     *        сама модель
     */
    void update(long elapsedMs, long wallMs, Map<Seat, Integer> levels, boolean ignitionOn,
            Air air) {
        if (disabled) {
            return;
        }
        long started = System.nanoTime();
        Double cabinCelsius = air.cabin(elapsedMs);
        Double outsideCelsius = air.outside(elapsedMs);
        try {
            trackLevels(elapsedMs, levels);
            if (!advance(elapsedMs, wallMs, levels, ignitionOn, cabinCelsius, outsideCelsius)) {
                return;
            }
            if (ignitionOn) {
                decide(elapsedMs, levels, cabinCelsius);
            } else {
                decisions.clear();
                coasting.clear();
                lastDecisionAtMs.clear();
            }
        } catch (Throwable t) {
            disabled = true;
            observers = null;
            log("ВНИМАНИЕ: фоновая модель отключена до перезапуска: " + t);
            return;
        }
        long tookMs = (System.nanoTime() - started) / 1_000_000L;
        if (tookMs > SLOW_UPDATE_MS) {
            log("модель: расчёт " + tookMs + " мс");
        }
    }

    /**
     * Фоновая оценка одного сиденья: поверхность базового профиля и банка
     * нижней и верхней границей и решение термостата.
     *
     * Свой тип, а не строка ряда калибровки: считающий слой не должен знать про
     * того, кто его записывает, иначе столбец в CSV не добавить, не тронув
     * расчёт. Перекладывает поля вызывающий.
     */
    static final class Estimate {
        final SeatObserver.Range base;
        final SeatObserver.Range bank;
        /** null — решения нет: зажигание не ON или минута ещё не прошла. */
        final Integer level;
        /** Откуда взялось начальное состояние наблюдателя; см. SeatObserver.origin. */
        final String origin;

        Estimate(SeatObserver.Range base, SeatObserver.Range bank, Integer level, String origin) {
            this.base = base;
            this.bank = bank;
            this.level = level;
            this.origin = origin;
        }
    }

    /** Оценка и решение по сиденью; null — фон не готов или отключён. */
    Estimate estimate(Seat seat) {
        if (observers == null) {
            return null;
        }
        SeatObserver observer = observers.get(seat);
        SeatThermostat.Decision decision = decisions.get(seat);
        return new Estimate(observer.base(), observer.bank(),
                decision == null ? null : decision.level, observer.origin());
    }

    /**
     * Смена подтверждённого уровня запускает задержку повышения. Первый
     * увиденный в процессе уровень её не запускает: при первом запуске
     * ожидание искусственно не добавляется. Неизвестный уровень увиденным не
     * считается — иначе первое удачное чтение сошло бы за смену.
     */
    private void trackLevels(long elapsedMs, Map<Seat, Integer> levels) {
        for (Seat seat : Seat.values()) {
            Integer level = levels.get(seat);
            if (level == null) {
                continue;
            }
            Integer seen = seenLevels.put(seat, level);
            if (seen != null && !seen.equals(level)) {
                levelChangedAtMs.put(seat, elapsedMs);
                // После внешнего изменения/ограничения прежний план мог бы
                // немедленно повысить уровень, обойдя трёхминутную задержку.
                lastDecisionAtMs.remove(seat);
            }
        }
    }

    /**
     * Досчёт наблюдателей и снимок. false — наблюдателей нет: воздух ещё не был
     * достоверен. Без салона уже созданные наблюдатели откладывают промежуток:
     * он досчитается оболочкой, когда воздух вернётся.
     */
    private boolean advance(long elapsedMs, long wallMs, Map<Seat, Integer> levels,
            boolean ignitionOn, Double cabinCelsius, Double outsideCelsius) {
        if (observers == null) {
            if (cabinCelsius == null) {
                return false;
            }
            observers = new EnumMap<>(Seat.class);
            for (Seat seat : Seat.values()) {
                long started = System.nanoTime();
                SeatObserver observer = SeatObserver.restore(seat, store.load(seat), wallMs,
                        elapsedMs, cabinCelsius, outsideCelsius, levels.get(seat), ignitionOn);
                observers.put(seat, observer);
                log("модель, " + seat.title + ": " + observer.origin() + ", расчёт "
                        + (System.nanoTime() - started) / 1_000_000L + " мс");
            }
        } else {
            for (Seat seat : Seat.values()) {
                observers.get(seat).observe(elapsedMs, levels.get(seat), ignitionOn,
                        cabinCelsius, outsideCelsius);
            }
        }
        Map<Seat, String> snapshots = new EnumMap<>(Seat.class);
        for (Seat seat : Seat.values()) {
            snapshots.put(seat, observers.get(seat).encode(wallMs));
        }
        store.save(snapshots);
        return true;
    }

    /**
     * Прогноз перегрева — на каждом обновлении, обычный выбор — раз в минуту.
     * Оба нуля ждать минуты не должны: пока держится прогноз перегрева,
     * обычный выбор не публикуется, а недостоверный салон даёт ноль сразу.
     * Неизвестный уровень для плана считается нулём.
     */
    private void decide(long elapsedMs, Map<Seat, Integer> levels, Double cabinCelsius) {
        for (Seat seat : Seat.values()) {
            Long last = lastDecisionAtMs.get(seat);
            boolean regular = last == null || elapsedMs - last >= DECISION_INTERVAL_MS;
            if (regular) {
                lastDecisionAtMs.put(seat, elapsedMs);
            }
            SeatObserver observer = observers.get(seat);
            Integer known = levels.get(seat);
            int current = known == null ? 0 : known;
            /** Список строится заново на каждый вызов, а внутри цикла не меняется. */
            List<SeatThermostat.Scenario> scenarios = observer.scenarios();
            boolean coast = SeatThermostat.mustCoast(scenarios, current);
            boolean wasCoasting = Boolean.TRUE.equals(coasting.put(seat, coast));
            if (coast && !wasCoasting) {
                publish(seat, new SeatThermostat.Decision(0, COAST_REASON), observer);
            }
            if (!coast && (regular || cabinCelsius == null)) {
                Long changedAt = levelChangedAtMs.get(seat);
                double sinceChange = changedAt == null
                        ? Double.POSITIVE_INFINITY
                        : (elapsedMs - changedAt) / SeatObserver.MINUTE_MS;
                publish(seat, SeatThermostat.choose(scenarios, current, sinceChange,
                        cabinCelsius), observer);
            }
        }
    }

    /**
     * В лог — только смена уровня или смена вида причины. Планы меняются
     * каждую минуту при том же уровне, и строка на каждый забила бы буфер.
     */
    private void publish(Seat seat, SeatThermostat.Decision decision, SeatObserver observer) {
        SeatThermostat.Decision previous = decisions.put(seat, decision);
        if (previous != null && previous.level == decision.level
                && kind(previous.reason).equals(kind(decision.reason))) {
            return;
        }
        SeatObserver.Range base = observer.base();
        SeatObserver.Range bank = observer.bank();
        log(String.format(Locale.US,
                "модель, %s: %d (%s; поверхность база %.1f…%.1f, банк %.1f…%.1f)",
                seat.title, decision.level, decision.reason, base.low, base.high, bank.low,
                bank.high));
    }

    private static String kind(String reason) {
        return reason.startsWith("план ") ? "план" : reason;
    }

    private void log(String message) {
        if (logSink != null) {
            logSink.log(message);
        }
    }
}
