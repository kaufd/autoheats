package com.wt.airconditioner;

/**
 * Таблица расписаний каскада по температуре салона. Значения настроены по
 * живым поездкам — менять их только по новым замерам.
 *
 * Диапазон задаёт две вещи: сколько уровень держится максимум (таймер) и при
 * какой температуре можно шагнуть вниз досрочно (порог). Чем холоднее, тем
 * дольше держим и тем ниже порог.
 *
 * Класс существует ровно столько, сколько каскад: тепловая модель
 * ({@link SeatThermalModel}) заменит эти тридцать констант расчётом.
 */
final class TemperatureConstants {

    /** Выше этой температуры подогрев не нужен вовсе. */
    static final double OFF_ABOVE_CELSIUS = 10.0;

    private TemperatureConstants() {
    }

    private static final HeatSequence WARM = new HeatSequence(3, 2, 5, 6.0, 8.0, 11.0);
    private static final HeatSequence COOL = new HeatSequence(5, 3, 7, 2.0, 6.0, 9.0);
    /**
     * Замер под человеком при 0…−5 °C: на тройке 7–8 минут, дальше горячо и
     * двойка лишняя — сразу на единицу, её хватает на 5 минут. Нулевая
     * длительность двойки и есть «пропустить» (см. AutoHeatEngine.stepDown).
     */
    private static final HeatSequence COLD = new HeatSequence(7, 0, 5, -2.0, 3.0, 7.0);
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
