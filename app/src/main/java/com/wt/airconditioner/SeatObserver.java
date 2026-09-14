package com.wt.airconditioner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Оценка одного сиденья по тепловой модели: что сейчас с матом и поверхностью,
 * если верна та или иная гипотеза банка профилей. Датчика поверхности нет,
 * поэтому это не измерение, а набор историй под фактическими уровнями,
 * воздухом и зажиганием.
 *
 * Каждая история хранится нижней и верхней границей. Пассивная схема с
 * ограничителем уставки монотонна по состоянию, поэтому обе границы идут
 * одними уравнениями, а неизвестный воздух на стоянке и неизвестная история
 * расширяют оболочку, не множа сценарии.
 *
 * Ни одной ссылки на Android: время и воздух подаёт сервис. Дизайн —
 * docs/superpowers/specs/2026-09-14-model-shadow-mode-design.md.
 */
final class SeatObserver {

    /** Меняется вместе с форматом снимка или составом банка. */
    private static final String SNAPSHOT_VERSION = "1";

    private static final String SEPARATOR = ";";

    /**
     * Доля улицы в окружении подушки — эффективная поправка на холодные
     * поверхности, раздел 2 спецификации.
     */
    private static final double OUTSIDE_WEIGHT = 0.2;

    /** Запас неизвестной истории к воздуху, раздел 3 спецификации. */
    private static final double UNKNOWN_MARGIN_CELSIUS = 3.0;

    /** Гипотеза «недавно грелось» для неизвестной истории. */
    private static final double HOT_SEAT_CELSIUS = 45.0;

    /**
     * Остановка длинного досчёта: все узлы в полосе ±0.005 °C от окружения.
     * Дальше они только приближаются к нему, так что расхождение с полным
     * расчётом не больше 0.01 °C.
     */
    private static final double CONVERGED_CELSIUS = 0.005;

    /** Между проверками сближения; на точность не влияет. */
    private static final double CHUNK_MINUTES = 1.0;

    /**
     * Перерыв, после которого воздух может прийти устаревшим: голова засыпает
     * через 10 минут после ACC, а после пробуждения ~50 секунд отдаёт салон
     * прошлой сессии (замер 14.09).
     */
    private static final double LONG_GAP_MINUTES = 10.0;

    /**
     * Сколько после долгого перерыва новый воздух ещё пересчитывает сам
     * перерыв. Дальше изменение воздуха — погода или печка, а не устаревшее
     * значение.
     */
    private static final double SETTLE_MINUTES = 2.0;

    private static final double MINUTE_MS = 60_000.0;

    /** Банк раздела 6 спецификации: p, r, множитель cₐ. */
    private static final double[][] HEAT_EXCHANGE = {
            {43.0, 1.0, 1.0}, {43.0 * 0.7, 0.5, 2.0}, {43.0 * 1.3, 2.0, 0.5}};

    /** Доли мощности уровней: ШИМ и «различаются только уставки». */
    private static final double[][] DUTIES = {{0.0, 0.40, 0.66, 1.0}, {0.0, 1.0, 1.0, 1.0}};

    /** Контакт: множитель cₐ, cᵦ, B. Первый — пустое кресло. */
    private static final double[] EMPTY = {1.0, 0.0, 33.0};
    private static final double[][] CONTACTS = {EMPTY, {0.75, 0.02, 32.0}, {0.5, 0.08, 35.0}};

    /** Поверхность нижней и верхней оценки. */
    static final class Range {
        final double low;
        final double high;

        Range(double low, double high) {
            this.low = low;
            this.high = high;
        }
    }

    /**
     * Одна гипотеза во времени. Контакт профиля действует только при ON: на
     * стоянке кресло пустое.
     */
    private static final class History {
        final SeatThermalModel.Parameters whileOn;
        final SeatThermalModel.Parameters parked;
        SeatThermalModel.State low;
        SeatThermalModel.State high;

        History(SeatThermalModel.Parameters whileOn, SeatThermalModel.Parameters parked) {
            this.whileOn = whileOn;
            this.parked = parked;
        }

        SeatThermalModel.Parameters parameters(boolean ignitionOn) {
            return ignitionOn ? whileOn : parked;
        }
    }

    /** Отрезок досчёта с уровнем и зажиганием его начала. */
    private static final class Segment {
        final double minutes;
        final Integer level;
        final boolean ignitionOn;
        double lowEnvironment;
        double highEnvironment;

        Segment(double minutes, Integer level, boolean ignitionOn, double environmentStart,
                double environmentEnd) {
            this.minutes = minutes;
            this.level = level;
            this.ignitionOn = ignitionOn;
            this.lowEnvironment = Math.min(environmentStart, environmentEnd);
            this.highEnvironment = Math.max(environmentStart, environmentEnd);
        }
    }

    /**
     * Начало долгого перерыва и всё досчитанное после него, пока воздух мог
     * быть устаревшим. Свежий воздух, вышедший за оболочку перерыва, расширяет
     * её, и отрезки прогоняются заново — «пересчитывается при смене значения,
     * а не фиксируется по первому отсчёту» (раздел 7 спецификации модели).
     */
    private static final class Replay {
        /** Состояния до перерыва; null — история неизвестна и строится от воздуха. */
        final SeatThermalModel.State[] start;
        double coldestAir;
        double warmestAir;
        final List<Segment> segments = new ArrayList<>();
        double minutesAfterGap;

        Replay(SeatThermalModel.State[] start, double coldestAir, double warmestAir) {
            this.start = start;
            this.coldestAir = coldestAir;
            this.warmestAir = warmestAir;
        }
    }

    /**
     * Базовый профиль, совпавший с «жарко» 12.09: одно число для сравнения с
     * ощущением. В сценарии термостата не входит.
     */
    private final History base;
    private final List<History> histories;

    private String origin;
    /** null — окно пересчёта после долгого перерыва закрыто. */
    private Replay replay;
    /**
     * Промежутки без достоверного салона со своими уровнем и зажиганием.
     * Досчитываются, когда воздух вернётся; состояние и снимок — на их начало.
     */
    private final List<Segment> pending = new ArrayList<>();
    /** Настенное время принятого снимка; только для перерыва при восстановлении. */
    private long snapshotWallMs;
    private long lastElapsedMs;
    /** Последний подтверждённый уровень; null — не известен. */
    private Integer level;
    private boolean ignitionOn;
    private double environment;

    private SeatObserver(Seat seat) {
        SeatThermalModel.Parameters baseProfile = new SeatThermalModel.Parameters();
        this.base = new History(baseProfile, baseProfile);
        this.histories = bank(seat);
    }

    /** E = 0.8·салон + 0.2·улица; без достоверной улицы — салон. */
    static double environment(double cabinCelsius, Double outsideCelsius) {
        return outsideCelsius == null
                ? cabinCelsius
                : (1 - OUTSIDE_WEIGHT) * cabinCelsius + OUTSIDE_WEIGHT * outsideCelsius;
    }

    /**
     * Параметры историй сиденья при зажигании ON. У водителя пустого контакта
     * нет — допущение раздела 2: режим привязан к ON, водитель на месте. У
     * пассажира пустое кресло обычно.
     */
    static List<SeatThermalModel.Parameters> profilesWhileOn(Seat seat) {
        List<SeatThermalModel.Parameters> profiles = new ArrayList<>();
        for (History history : bank(seat)) {
            profiles.add(history.whileOn);
        }
        return profiles;
    }

    /**
     * Наблюдатель после старта процесса. Снимок годится — перерыв досчитывается
     * по настенному времени с уровнем и зажиганием из снимка; нет, повреждён,
     * чужой версии или часы ушли назад — неизвестная история. Снимок из
     * поездки перед долгим перерывом — тоже: голова гаснет при OFF вместе с
     * процессом, и когда выключили зажигание и кто сидел, не узнать.
     *
     * @param level и ignitionOn — текущие, действуют с этого момента
     */
    static SeatObserver restore(Seat seat, String snapshot, long wallNowMs, long elapsedNowMs,
            double cabinCelsius, Double outsideCelsius, Integer level, boolean ignitionOn) {
        SeatObserver observer = new SeatObserver(seat);
        double now = environment(cabinCelsius, outsideCelsius);
        String failure = observer.load(snapshot, wallNowMs);
        double gapMinutes = (wallNowMs - observer.snapshotWallMs) / MINUTE_MS;
        if (failure == null && observer.ignitionOn && gapMinutes >= LONG_GAP_MINUTES) {
            failure = String.format(Locale.US, "снимок из поездки, перерыв %.1f мин", gapMinutes);
        }
        if (failure == null) {
            observer.origin = String.format(Locale.US, "снимок, перерыв %.1f мин", gapMinutes);
            observer.step(new Segment(gapMinutes, observer.level, observer.ignitionOn,
                    observer.environment, now), now, cabinCelsius, outsideCelsius);
        } else {
            observer.origin = "неизвестная история: " + failure;
            /** Воздух при старте тоже может быть устаревшим — окно открыто. */
            observer.replay = new Replay(null, coldest(cabinCelsius, outsideCelsius),
                    warmest(cabinCelsius, outsideCelsius));
            observer.startUnknown(observer.replay.coldestAir, observer.replay.warmestAir);
        }
        observer.lastElapsedMs = elapsedNowMs;
        observer.level = level;
        observer.ignitionOn = ignitionOn;
        observer.environment = now;
        return observer;
    }

    /** Откуда взялось начальное состояние — для лога. */
    String origin() {
        return origin;
    }

    /**
     * Досчитывает промежуток с прошлого наблюдения и запоминает новые входы.
     * На промежутке действуют уровень и зажигание его начала: новое значение,
     * пришедшее событием, относится к будущему.
     *
     * Без салона промежуток откладывается: смена уровня или зажигания в это
     * время не теряется, а окружение ему — оболочка между последним воздухом
     * и вернувшимся.
     */
    void observe(long elapsedMs, Integer level, boolean ignitionOn, Double cabinCelsius,
            Double outsideCelsius) {
        pending.add(new Segment(Math.max(0L, elapsedMs - lastElapsedMs) / MINUTE_MS,
                this.level, this.ignitionOn, environment, environment));
        lastElapsedMs = elapsedMs;
        this.level = level;
        this.ignitionOn = ignitionOn;
        if (cabinCelsius == null) {
            return;
        }
        double now = environment(cabinCelsius, outsideCelsius);
        for (Segment segment : pending) {
            segment.lowEnvironment = Math.min(segment.lowEnvironment, now);
            segment.highEnvironment = Math.max(segment.highEnvironment, now);
            step(segment, now, cabinCelsius, outsideCelsius);
        }
        pending.clear();
        environment = now;
    }

    /**
     * Сценарии для термостата: обе границы каждой истории с текущим контактом
     * и текущим окружением на весь горизонт.
     */
    List<SeatThermostat.Scenario> scenarios() {
        List<SeatThermostat.Scenario> scenarios = new ArrayList<>();
        for (History history : histories) {
            SeatThermalModel.Parameters parameters = history.parameters(ignitionOn);
            scenarios.add(new SeatThermostat.Scenario(history.low, parameters, environment));
            scenarios.add(new SeatThermostat.Scenario(history.high, parameters, environment));
        }
        return Collections.unmodifiableList(scenarios);
    }

    Range base() {
        return new Range(base.low.surface, base.high.surface);
    }

    /** Минимум нижних и максимум верхних поверхностей по банку. */
    Range bank() {
        double low = Double.POSITIVE_INFINITY;
        double high = Double.NEGATIVE_INFINITY;
        for (History history : histories) {
            low = Math.min(low, history.low.surface);
            high = Math.max(high, history.high.surface);
        }
        return new Range(low, high);
    }

    /**
     * Снимок: версия; настенное время; уровень (пусто — неизвестен); зажигание;
     * окружение; базовый профиль; истории — «нижний мат,нижняя поверхность,
     * верхний мат,верхняя поверхность». Double.toString читается обратно без
     * потерь.
     *
     * Время, уровень и зажигание — на момент досчитанного состояния, а не
     * «сейчас»: отложенное без воздуха досчитает перерыв после перезапуска.
     * Смены внутри отложенного при этом теряются.
     */
    String encode(long wallMs) {
        double pendingMinutes = 0;
        for (Segment segment : pending) {
            pendingMinutes += segment.minutes;
        }
        Integer countedLevel = pending.isEmpty() ? level : pending.get(0).level;
        boolean countedIgnition = pending.isEmpty() ? ignitionOn : pending.get(0).ignitionOn;
        StringBuilder text = new StringBuilder(SNAPSHOT_VERSION);
        text.append(SEPARATOR).append(wallMs - Math.round(pendingMinutes * MINUTE_MS));
        text.append(SEPARATOR).append(countedLevel == null ? "" : String.valueOf(countedLevel));
        text.append(SEPARATOR).append(countedIgnition ? '1' : '0');
        text.append(SEPARATOR).append(environment);
        appendHistory(text, base);
        for (History history : histories) {
            appendHistory(text, history);
        }
        return text.toString();
    }

    private static void appendHistory(StringBuilder text, History history) {
        text.append(SEPARATOR).append(history.low.mat).append(',').append(history.low.surface)
                .append(',').append(history.high.mat).append(',').append(history.high.surface);
    }

    /** Разбор снимка в поля. null — снимок принят, иначе причина отказа. */
    private String load(String snapshot, long wallNowMs) {
        if (snapshot == null) {
            return "снимка нет";
        }
        String[] parts = snapshot.split(SEPARATOR, -1);
        if (!SNAPSHOT_VERSION.equals(parts[0])) {
            return "чужая версия снимка";
        }
        if (parts.length != 6 + histories.size()) {
            return "снимок повреждён";
        }
        try {
            long wallMs = Long.parseLong(parts[1]);
            if (wallMs > wallNowMs) {
                return "часы ушли назад";
            }
            Integer savedLevel = parts[2].isEmpty() ? null : Integer.parseInt(parts[2]);
            if (savedLevel != null && (savedLevel < 0 || savedLevel > 3)
                    || !("0".equals(parts[3]) || "1".equals(parts[3]))) {
                return "снимок повреждён";
            }
            double savedEnvironment = Double.parseDouble(parts[4]);
            if (!Double.isFinite(savedEnvironment)
                    || !parseHistory(parts[5], base)) {
                return "снимок повреждён";
            }
            for (int i = 0; i < histories.size(); i++) {
                if (!parseHistory(parts[6 + i], histories.get(i))) {
                    return "снимок повреждён";
                }
            }
            snapshotWallMs = wallMs;
            level = savedLevel;
            ignitionOn = "1".equals(parts[3]);
            environment = savedEnvironment;
            return null;
        } catch (NumberFormatException e) {
            return "снимок повреждён";
        }
    }

    private static boolean parseHistory(String text, History history) {
        String[] values = text.split(",", -1);
        if (values.length != 4) {
            return false;
        }
        double[] numbers = new double[4];
        for (int i = 0; i < 4; i++) {
            numbers[i] = Double.parseDouble(values[i]);
            if (!Double.isFinite(numbers[i])) {
                return false;
            }
        }
        history.low = new SeatThermalModel.State(numbers[0], numbers[1]);
        history.high = new SeatThermalModel.State(numbers[2], numbers[3]);
        return true;
    }

    private static double coldest(double cabinCelsius, Double outsideCelsius) {
        return outsideCelsius == null ? cabinCelsius : Math.min(cabinCelsius, outsideCelsius);
    }

    private static double warmest(double cabinCelsius, Double outsideCelsius) {
        return outsideCelsius == null ? cabinCelsius : Math.max(cabinCelsius, outsideCelsius);
    }

    /** Раздел 3 спецификации: остывшее, прогретое воздухом, недавно грелось. */
    private void startUnknown(double coldestAir, double warmestAir) {
        double low = coldestAir - UNKNOWN_MARGIN_CELSIUS;
        double high = Math.max(HOT_SEAT_CELSIUS, warmestAir + UNKNOWN_MARGIN_CELSIUS);
        base.low = new SeatThermalModel.State(low, low);
        base.high = new SeatThermalModel.State(high, high);
        for (History history : histories) {
            history.low = base.low;
            history.high = base.high;
        }
    }

    /**
     * Отрезок до нынешнего воздуха. Долгий перерыв открывает окно пересчёта; в
     * окне воздух, вышедший за оболочку перерыва, прогоняет перерыв и всё после
     * него заново.
     */
    private void step(Segment segment, double now, double cabinCelsius, Double outsideCelsius) {
        if (replay != null) {
            replay.minutesAfterGap += segment.minutes;
            if (replay.minutesAfterGap > SETTLE_MINUTES) {
                replay = null;
            } else {
                replay.segments.add(segment);
                if (widen(replay, now, cabinCelsius, outsideCelsius)) {
                    rerun(replay);
                } else {
                    advance(segment);
                }
                return;
            }
        }
        if (segment.minutes >= LONG_GAP_MINUTES) {
            replay = new Replay(states(), now, now);
            replay.segments.add(segment);
        }
        advance(segment);
    }

    /** true — воздух вышел за то, чем посчитан перерыв. */
    private static boolean widen(Replay replay, double now, double cabinCelsius,
            Double outsideCelsius) {
        if (replay.start == null) {
            double cold = coldest(cabinCelsius, outsideCelsius);
            double warm = warmest(cabinCelsius, outsideCelsius);
            boolean widened = cold < replay.coldestAir || warm > replay.warmestAir;
            replay.coldestAir = Math.min(replay.coldestAir, cold);
            replay.warmestAir = Math.max(replay.warmestAir, warm);
            return widened;
        }
        Segment gap = replay.segments.get(0);
        boolean widened = now < gap.lowEnvironment || now > gap.highEnvironment;
        gap.lowEnvironment = Math.min(gap.lowEnvironment, now);
        gap.highEnvironment = Math.max(gap.highEnvironment, now);
        return widened;
    }

    private void rerun(Replay replay) {
        if (replay.start == null) {
            startUnknown(replay.coldestAir, replay.warmestAir);
        } else {
            restoreStates(replay.start);
        }
        for (Segment segment : replay.segments) {
            advance(segment);
        }
    }

    /** Нижняя и верхняя граница базового профиля, затем историй — по порядку. */
    private SeatThermalModel.State[] states() {
        SeatThermalModel.State[] states = new SeatThermalModel.State[2 * (1 + histories.size())];
        states[0] = base.low;
        states[1] = base.high;
        for (int i = 0; i < histories.size(); i++) {
            states[2 + 2 * i] = histories.get(i).low;
            states[3 + 2 * i] = histories.get(i).high;
        }
        return states;
    }

    private void restoreStates(SeatThermalModel.State[] states) {
        base.low = states[0];
        base.high = states[1];
        for (int i = 0; i < histories.size(); i++) {
            histories.get(i).low = states[2 + 2 * i];
            histories.get(i).high = states[3 + 2 * i];
        }
    }

    /**
     * Отрезок под его уровнем и зажиганием. Нижняя граница — с меньшим
     * окружением и без нагрева, если уровень неизвестен или зажигание не ON:
     * подогрев обесточивается вместе с зажиганием, но выключение не
     * подтверждено. Верхняя — с большим окружением и тройкой при неизвестном
     * уровне.
     *
     * Неподтверждённый нагрев дольше долгого перерыва — неизвестная история
     * от воздуха отрезка: сколько грело, не узнать, а досчёт без ранней
     * остановки на главном потоке ничем не ограничен.
     */
    private void advance(Segment segment) {
        int lowLevel = segment.level == null || !segment.ignitionOn ? 0 : segment.level;
        int highLevel = segment.level == null ? 3 : segment.level;
        double low = segment.lowEnvironment;
        double high = segment.highEnvironment;
        if (highLevel > 0 && !segment.ignitionOn && segment.minutes >= LONG_GAP_MINUTES) {
            startUnknown(low, high);
            return;
        }
        double remaining = segment.minutes;
        while (remaining > 0) {
            double chunk = Math.min(remaining, CHUNK_MINUTES);
            advance(base, segment.ignitionOn, chunk, lowLevel, highLevel, low, high);
            for (History history : histories) {
                advance(history, segment.ignitionOn, chunk, lowLevel, highLevel, low, high);
            }
            remaining -= chunk;
            if (highLevel == 0 && !segment.ignitionOn && metTheAir(low, high)) {
                return;
            }
        }
    }

    private static void advance(History history, boolean ignitionOn, double minutes,
            int lowLevel, int highLevel, double lowEnvironment, double highEnvironment) {
        SeatThermalModel.Parameters parameters = history.parameters(ignitionOn);
        history.low = SeatThermalModel.advance(history.low, lowLevel, lowEnvironment,
                parameters, minutes);
        history.high = SeatThermalModel.advance(history.high, highLevel, highEnvironment,
                parameters, minutes);
    }

    /**
     * Без нагрева и без человека равновесие — само окружение: дальше узлы к нему
     * только приближаются.
     */
    private boolean metTheAir(double lowEnvironment, double highEnvironment) {
        if (!near(base, lowEnvironment, highEnvironment)) {
            return false;
        }
        for (History history : histories) {
            if (!near(history, lowEnvironment, highEnvironment)) {
                return false;
            }
        }
        return true;
    }

    private static boolean near(History history, double lowEnvironment, double highEnvironment) {
        return Math.abs(history.low.mat - lowEnvironment) <= CONVERGED_CELSIUS
                && Math.abs(history.low.surface - lowEnvironment) <= CONVERGED_CELSIUS
                && Math.abs(history.high.mat - highEnvironment) <= CONVERGED_CELSIUS
                && Math.abs(history.high.surface - highEnvironment) <= CONVERGED_CELSIUS;
    }

    private static List<History> bank(Seat seat) {
        List<History> bank = new ArrayList<>();
        for (double[] exchange : HEAT_EXCHANGE) {
            for (double[] duty : DUTIES) {
                for (double[] contact : CONTACTS) {
                    if (seat == Seat.DRIVER && contact == EMPTY) {
                        continue;
                    }
                    bank.add(new History(profile(exchange, duty, contact),
                            profile(exchange, duty, EMPTY)));
                }
            }
        }
        return bank;
    }

    private static SeatThermalModel.Parameters profile(double[] exchange, double[] duty,
            double[] contact) {
        SeatThermalModel.Parameters parameters = new SeatThermalModel.Parameters();
        parameters.p = exchange[0];
        parameters.r = exchange[1];
        parameters.air = parameters.air * exchange[2] * contact[0];
        parameters.duty = duty.clone();
        parameters.body = contact[1];
        parameters.bodyCelsius = contact[2];
        return parameters;
    }
}
