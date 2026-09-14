package com.wt.airconditioner;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Тепловая модель в фоне: наблюдатели обоих сидений идут по фактическим
 * уровням, термостат считает решение, и оба только пишутся в лог и ряд. В
 * машину решение не уходит — авто-режим по-прежнему ведёт AutoHeatEngine, пока
 * параметры теплообмена не откалиброваны.
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
    private final AutoHeatEngine.LogSink logSink;

    /** null — воздух ещё ни разу не был достоверен. */
    private Map<Seat, SeatObserver> observers;
    private boolean disabled;

    private final Map<Seat, SeatThermostat.Decision> decisions = new EnumMap<>(Seat.class);
    private final Map<Seat, Boolean> coasting = new EnumMap<>(Seat.class);
    /** Последний увиденный уровень и когда он сменился; нет ключа — не сменялся. */
    private final Map<Seat, Integer> seenLevels = new EnumMap<>(Seat.class);
    private final Map<Seat, Long> levelChangedAtMs = new EnumMap<>(Seat.class);
    private Long lastDecisionAtMs;

    ModelShadow(Store store, AutoHeatEngine.LogSink logSink) {
        this.store = store;
        this.logSink = logSink;
    }

    /**
     * Обновление на тике ряда и на событиях уровня и зажигания. Сбой любого
     * рода отключает фон до перезапуска процесса и наружу не выходит: подогрев
     * от фонового расчёта зависеть не должен.
     *
     * @param levels подтверждённые автомобилем уровни; нет ключа — не известен
     * @param cabinCelsius достоверный салон или null
     * @param outsideCelsius достоверная улица или null
     */
    void update(long elapsedMs, long wallMs, Map<Seat, Integer> levels, boolean ignitionOn,
            Double cabinCelsius, Double outsideCelsius) {
        if (disabled) {
            return;
        }
        long started = System.nanoTime();
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
                lastDecisionAtMs = null;
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

    /** Оценка и решение для ряда; null — фон не готов или отключён. */
    CalibrationLog.Model model(Seat seat) {
        if (observers == null) {
            return null;
        }
        SeatObserver observer = observers.get(seat);
        SeatObserver.Range base = observer.base();
        SeatObserver.Range bank = observer.bank();
        CalibrationLog.Model model = new CalibrationLog.Model();
        model.baseLow = base.low;
        model.baseHigh = base.high;
        model.bankLow = bank.low;
        model.bankHigh = bank.high;
        SeatThermostat.Decision decision = decisions.get(seat);
        model.level = decision == null ? null : decision.level;
        return model;
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
        boolean regular = lastDecisionAtMs == null
                || elapsedMs - lastDecisionAtMs >= DECISION_INTERVAL_MS;
        if (regular) {
            lastDecisionAtMs = elapsedMs;
        }
        for (Seat seat : Seat.values()) {
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
