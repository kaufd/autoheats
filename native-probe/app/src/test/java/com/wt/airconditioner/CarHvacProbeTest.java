package com.wt.airconditioner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * Разбор значения датчика температуры. Проверяется тестом, потому что на
 * эмуляторе этот путь недостижим (там нет android.car), а на голове он
 * выполняется вслепую — и ошибка здесь выглядит как рабочая температура.
 */
public class CarHvacProbeTest {

    @Test
    public void parsesIntegerValue() {
        assertEquals(Integer.valueOf(124), CarHvacProbe.parseRaw(124));
    }

    @Test
    public void parsesStringValue() {
        // HvacService принимает значение как String/int/double — голова шлёт
        // и строки тоже.
        assertEquals(Integer.valueOf(124), CarHvacProbe.parseRaw("124"));
        assertEquals(Integer.valueOf(124), CarHvacProbe.parseRaw(" 124.0 "));
    }

    @Test
    public void keepsSentinelForCaller() {
        // parseRaw только разбирает; отсечение raw < 0 — на вызывающем.
        assertEquals(Integer.valueOf(-1), CarHvacProbe.parseRaw(-1));
    }

    @Test
    public void rejectsUnparseableValues() {
        // Раньше здесь получался 0, то есть ложные -42 °C.
        assertNull(CarHvacProbe.parseRaw("нет данных"));
        assertNull(CarHvacProbe.parseRaw(null));
        assertNull(CarHvacProbe.parseRaw(new Object()));
    }

    // --- что именно попадёт на экран: null = температура не публикуется ---

    @Test
    public void publishesValidReading() {
        assertEquals(Double.valueOf(20.0), CarHvacProbe.celsiusToPublish(124));
        assertEquals(Double.valueOf(20.0), CarHvacProbe.celsiusToPublish("124"));
        assertEquals(Double.valueOf(-0.5), CarHvacProbe.celsiusToPublish(83));
    }

    @Test
    public void doesNotPublishSentinel() {
        // Суть фикса: -1 дал бы "-42.5 °C" — ложный вывод об исправном датчике.
        assertNull(CarHvacProbe.celsiusToPublish(-1));
        assertNull(CarHvacProbe.celsiusToPublish("-1"));
    }

    @Test
    public void doesNotPublishUnparseableValue() {
        assertNull(CarHvacProbe.celsiusToPublish("нет данных"));
        assertNull(CarHvacProbe.celsiusToPublish(null));
        assertNull(CarHvacProbe.celsiusToPublish(new Object()));
    }

    @Test
    public void publishesZeroRawAsItsRealValue() {
        // raw = 0 валиден (-42 °C): sentinel — это строго отрицательное.
        assertEquals(Double.valueOf(-42.0), CarHvacProbe.celsiusToPublish(0));
    }
}
