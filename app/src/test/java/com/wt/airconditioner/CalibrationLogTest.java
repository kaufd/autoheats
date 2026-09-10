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
        return sample;
    }

    @Test
    public void writesEveryColumnInHeaderOrder() {
        assertEquals("2026-09-11 08:30:00;tick;124;20.0;108;88.50 0.00;3 7;2;0;1",
                CalibrationLog.row("2026-09-11 08:30:00", sample()));
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
        assertEquals("t;;;;;;;;;", CalibrationLog.row("t", empty));
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
