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

    /**
     * Базовый профиль, совпавший с «жарко» 12.09: одно число для сравнения с
     * ощущением. В сценарии термостата не входит.
     */
    private final History base;
    private final List<History> histories;

    private String origin;
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
     * чужой версии или часы ушли назад — неизвестная история.
     *
     * @param level и ignitionOn — текущие, действуют с этого момента
     */
    static SeatObserver restore(Seat seat, String snapshot, long wallNowMs, long elapsedNowMs,
            double cabinCelsius, Double outsideCelsius, Integer level, boolean ignitionOn) {
        SeatObserver observer = new SeatObserver(seat);
        double now = environment(cabinCelsius, outsideCelsius);
        String failure = observer.load(snapshot, wallNowMs);
        if (failure == null) {
            double gapMinutes = (wallNowMs - observer.snapshotWallMs) / MINUTE_MS;
            observer.origin = String.format(Locale.US, "снимок, перерыв %.1f мин", gapMinutes);
            observer.advance(gapMinutes, observer.environment, now);
        } else {
            observer.origin = "неизвестная история: " + failure;
            observer.startUnknown(cabinCelsius, outsideCelsius);
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
     */
    void observe(long elapsedMs, Integer level, boolean ignitionOn, double cabinCelsius,
            Double outsideCelsius) {
        double now = environment(cabinCelsius, outsideCelsius);
        advance(Math.max(0L, elapsedMs - lastElapsedMs) / MINUTE_MS, environment, now);
        lastElapsedMs = elapsedMs;
        this.level = level;
        this.ignitionOn = ignitionOn;
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
     */
    String encode(long wallMs) {
        StringBuilder text = new StringBuilder(SNAPSHOT_VERSION);
        text.append(SEPARATOR).append(wallMs);
        text.append(SEPARATOR).append(level == null ? "" : String.valueOf(level));
        text.append(SEPARATOR).append(ignitionOn ? '1' : '0');
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

    /** Раздел 3 спецификации: остывшее, прогретое воздухом, недавно грелось. */
    private void startUnknown(double cabinCelsius, Double outsideCelsius) {
        double outside = outsideCelsius == null ? cabinCelsius : outsideCelsius;
        double coldest = Math.min(cabinCelsius, outside);
        double warmest = Math.max(cabinCelsius, outside);
        double low = coldest - UNKNOWN_MARGIN_CELSIUS;
        double high = Math.max(HOT_SEAT_CELSIUS, warmest + UNKNOWN_MARGIN_CELSIUS);
        base.low = new SeatThermalModel.State(low, low);
        base.high = new SeatThermalModel.State(high, high);
        for (History history : histories) {
            history.low = base.low;
            history.high = base.high;
        }
    }

    /**
     * Промежуток под текущими уровнем и зажиганием. Нижняя граница — с меньшим
     * окружением и, при неизвестном уровне, без нагрева; верхняя — с большим и
     * тройкой.
     */
    private void advance(double minutes, double environmentStart, double environmentEnd) {
        double lowEnvironment = Math.min(environmentStart, environmentEnd);
        double highEnvironment = Math.max(environmentStart, environmentEnd);
        int lowLevel = level == null ? 0 : level;
        int highLevel = level == null ? 3 : level;
        double remaining = minutes;
        while (remaining > 0) {
            double chunk = Math.min(remaining, CHUNK_MINUTES);
            advance(base, chunk, lowLevel, highLevel, lowEnvironment, highEnvironment);
            for (History history : histories) {
                advance(history, chunk, lowLevel, highLevel, lowEnvironment, highEnvironment);
            }
            remaining -= chunk;
            if (highLevel == 0 && !ignitionOn && metTheAir(lowEnvironment, highEnvironment)) {
                return;
            }
        }
    }

    private void advance(History history, double minutes, int lowLevel, int highLevel,
            double lowEnvironment, double highEnvironment) {
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
