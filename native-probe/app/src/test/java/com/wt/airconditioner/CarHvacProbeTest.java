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

    // --- зажигание: ON только состояние 4, всё прочее выключает подогрев ---

    @Test
    public void treatsOnlyStateOnAsIgnitionOn() {
        assertEquals(Boolean.TRUE, CarHvacProbe.ignitionOn(new int[]{4}));
    }

    @Test
    public void treatsEveryOtherStateAsIgnitionOff() {
        // UNDEFINED, LOCK, OFF, ACC — правило из
        // BackgroundRuntimeController.handleIgnition. START сюда не входит.
        for (int state : new int[]{0, 1, 2, 3}) {
            assertEquals("состояние " + state, Boolean.FALSE,
                    CarHvacProbe.ignitionOn(new int[]{state}));
        }
    }

    @Test
    public void doesNotTreatStarterAsIgnitionOff() {
        // Лог с головы: ON → «не ON (5)» → выключили оба сиденья → ON, всё за
        // секунду. START — это запуск двигателя, а не уход из машины, поэтому
        // состояние не меняется. Здесь native расходится с Flutter-версией.
        assertNull(CarHvacProbe.ignitionOn(new int[]{5}));
    }

    @Test
    public void ignoresIgnitionEventWithoutValue() {
        // null означает «событие пропустить», а не «зажигание выключено»:
        // иначе пустое событие погасило бы подогрев на ходу.
        assertNull(CarHvacProbe.ignitionOn(null));
        assertNull(CarHvacProbe.ignitionOn(new int[0]));
    }

    @Test
    public void publishesZeroRawAsItsRealValue() {
        // raw = 0 валиден (-42 °C): sentinel — это строго отрицательное.
        assertEquals(Double.valueOf(-42.0), CarHvacProbe.celsiusToPublish(0));
    }
}
