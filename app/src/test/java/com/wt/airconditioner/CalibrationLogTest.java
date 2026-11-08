package com.wt.airconditioner;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Формат строки ряда калибровки. Проверяется тестом, потому что разбирать
 * записи придётся спустя недели после поездки: перепутанный или сдвинутый
 * столбец обнаружится там, где переснять данные уже нельзя.
 */
public class CalibrationLogTest {

    private static CalibrationLog.Sample sample() {
        CalibrationLog.Sample sample = new CalibrationLog.Sample();
        sample.reason = "tick";
        sample.cabinRaw = 124;
        sample.cabinCelsius = 20.0;
        sample.outsideRaw = 108;
        sample.coolantFloats = new float[] {88.5f, 0f};
        sample.coolantInts = new int[] {3, 7};
        sample.driverLevel = 2;
        sample.passengerLevel = 0;
        sample.ignitionOn = true;
        sample.driverModel = model(31.25, 33.0, 24.0, 38.5, 2, "снимок, перерыв 78.4 мин");
        sample.passengerModel = model(12.0, 45.0, 11.5, 45.0, 0, "неизвестная история: часы назад");
        return sample;
    }

    private static CalibrationLog.Model model(double baseLow, double baseHigh, double bankLow,
            double bankHigh, Integer level, String origin) {
        CalibrationLog.Model model = new CalibrationLog.Model();
        model.baseLow = baseLow;
        model.baseHigh = baseHigh;
        model.bankLow = bankLow;
        model.bankHigh = bankHigh;
        model.level = level;
        model.origin = origin;
        return model;
    }

    @Test
    public void writesEveryColumnInHeaderOrder() {
        assertEquals("2026-09-11 08:30:00;tick;124;20.0;108;88.50 0.00;3 7;2;0;1"
                        + ";31.3;33.0;24.0;38.5;2;снимок, перерыв 78.4 мин"
                        + ";12.0;45.0;11.5;45.0;0;неизвестная история: часы назад",
                CalibrationLog.row("2026-09-11 08:30:00", sample()));
    }

    @Test
    public void replacesSeparatorInsideOrigin() {
        /**
         * Происхождение — свободная строка: точка с запятой внутри неё сдвинула
         * бы все колонки соседнего сиденья и сломала бы разбор файла целиком.
         */
        CalibrationLog.Sample sample = new CalibrationLog.Sample();
        sample.driverModel = model(1.0, 2.0, 3.0, 4.0, 1, "снимок; перерыв 1.0 мин");
        String[] fields = CalibrationLog.row("t", sample).split(";", -1);
        assertEquals(CalibrationLog.HEADER.split(";", -1).length, fields.length);
        assertEquals("снимок, перерыв 1.0 мин", fields[15]);
    }

    @Test
    public void headerMatchesRowWidth() {
        assertEquals(CalibrationLog.HEADER.split(";", -1).length,
                CalibrationLog.row("t", sample()).split(";", -1).length);
    }

    @Test
    public void leavesUnknownValuesEmpty() {
        /**
         * Пустое поле, а не ноль: «датчик молчал» и «ноль градусов» при разборе
         * не должны выглядеть одинаково — на этом строится вся калибровка.
         */
        CalibrationLog.Sample empty = new CalibrationLog.Sample();
        assertEquals("t;;;;;;;;;;;;;;;;;;;;;", CalibrationLog.row("t", empty));
    }

    @Test
    public void modelWithoutDecisionLeavesOnlyLevelEmpty() {
        /**
         * Оценка есть всегда, пока наблюдатель готов, а решение — только при
         * ON: пустой уровень не должен сдвигать или прятать оценку.
         */
        CalibrationLog.Sample sample = new CalibrationLog.Sample();
        sample.driverModel = model(30.0, 30.0, 29.0, 31.0, null, "снимок, перерыв 0.2 мин");
        String[] fields = CalibrationLog.row("t", sample).split(";", -1);
        assertEquals("30.0;30.0;29.0;31.0;;снимок, перерыв 0.2 мин",
                String.join(";", java.util.Arrays.copyOfRange(fields, 10, 16)));
        assertEquals(";;;;;", String.join(";", java.util.Arrays.copyOfRange(fields, 16, 22)));
    }

    @Test
    public void keepsWholeCoolantArray() {
        /**
         * Какой индекс несёт температуру ОЖ на этой голове, неизвестно, поэтому
         * выбирать один из них при записи нельзя — только весь массив.
         */
        CalibrationLog.Sample sample = new CalibrationLog.Sample();
        sample.coolantFloats = new float[] {1.5f, 2.25f, 3f};
        assertEquals("1.50 2.25 3.00", CalibrationLog.row("t", sample).split(";", -1)[5]);
    }

    @Test
    public void formatsNumbersWithDot() {
        /**
         * Locale.US обязателен: в русской локали разделителем стала бы запятая,
         * которая здесь же служит… не разделителем полей — но CSV с плавающей
         * запятой всё равно разобрать не выйдет.
         */
        CalibrationLog.Sample sample = new CalibrationLog.Sample();
        sample.cabinCelsius = -12.5;
        assertEquals("-12.5", CalibrationLog.row("t", sample).split(";", -1)[3]);
    }
}
