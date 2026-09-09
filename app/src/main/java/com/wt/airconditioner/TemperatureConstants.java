package com.wt.airconditioner;

/**
 * Расписания подогрева по температуре салона. Порт
 * lib/src/constants/temperature_constants.dart — значения перенесены дословно,
 * они настроены по живым поездкам и менять их при переписывании нельзя.
 *
 * Диапазон задаёт две вещи: сколько уровень держится максимум (таймер) и при
 * какой температуре можно шагнуть вниз досрочно (порог). Чем холоднее, тем
 * дольше держим и тем ниже порог.
 */
final class TemperatureConstants {

    /** Выше этой температуры подогрев не нужен вовсе. */
    static final double OFF_ABOVE_CELSIUS = 10.0;

    private TemperatureConstants() {
    }

    /**
     * Длительности уровней 3/2/1 в минутах и температуры досрочного перехода.
     * Порог по умолчанию — минус бесконечность: у пресетов досрочных переходов
     * нет, каскад идёт строго по таймеру.
     */
    static final class HeatSequence {
        final int level3Minutes;
        final int level2Minutes;
        final int level1Minutes;
        final double level3StepDownCelsius;
        final double level2StepDownCelsius;
        final double level1StepDownCelsius;

        HeatSequence(int level3Minutes, int level2Minutes, int level1Minutes) {
            this(level3Minutes, level2Minutes, level1Minutes,
                    Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY);
        }

        HeatSequence(int level3Minutes, int level2Minutes, int level1Minutes,
                double level3StepDownCelsius, double level2StepDownCelsius,
                double level1StepDownCelsius) {
            this.level3Minutes = level3Minutes;
            this.level2Minutes = level2Minutes;
            this.level1Minutes = level1Minutes;
            this.level3StepDownCelsius = level3StepDownCelsius;
            this.level2StepDownCelsius = level2StepDownCelsius;
            this.level1StepDownCelsius = level1StepDownCelsius;
        }

        int minutesFor(int level) {
            switch (level) {
                case 3:
                    return level3Minutes;
                case 2:
                    return level2Minutes;
                case 1:
                    return level1Minutes;
                default:
                    return 0;
            }
        }

        /** Температура, при которой уровень можно снизить, не дожидаясь таймера. */
        double stepDownCelsiusFor(int level) {
            switch (level) {
                case 3:
                    return level3StepDownCelsius;
                case 2:
                    return level2StepDownCelsius;
                case 1:
                    return level1StepDownCelsius;
                default:
                    return Double.POSITIVE_INFINITY;
            }
        }

        /**
         * Сравнение планов: каскад, дошедший до нуля, перезапускается только
         * если расписание сменилось. Без этого повторное событие с той же
         * температурой снова включало бы уровень 3 — бесконечный цикл.
         */
        String planKey() {
            return level3Minutes + "," + level2Minutes + "," + level1Minutes + ","
                    + level3StepDownCelsius + "," + level2StepDownCelsius + ","
                    + level1StepDownCelsius;
        }
    }

    private static final HeatSequence WARM = new HeatSequence(3, 2, 5, 6.0, 8.0, 11.0);
    private static final HeatSequence COOL = new HeatSequence(5, 3, 7, 2.0, 6.0, 9.0);
    private static final HeatSequence COLD = new HeatSequence(8, 5, 7, -2.0, 3.0, 7.0);
    private static final HeatSequence FREEZING = new HeatSequence(12, 7, 7, -7.0, -2.0, 4.0);
    private static final HeatSequence EXTREME = new HeatSequence(15, 10, 8, -12.0, -7.0, 0.0);

    /** Расписание для температуры салона; null — теплее порога, греть не нужно. */
    static HeatSequence heatSequenceFor(double celsius) {
        if (celsius >= OFF_ABOVE_CELSIUS) {
            return null;
        }
        if (celsius >= 5.0) {
            return WARM;
        }
        if (celsius >= 0.0) {
            return COOL;
        }
        if (celsius >= -5.0) {
            return COLD;
        }
        if (celsius >= -10.0) {
            return FREEZING;
        }
        return EXTREME;
    }
}
