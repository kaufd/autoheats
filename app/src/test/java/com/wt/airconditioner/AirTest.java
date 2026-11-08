package com.wt.airconditioner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** Когда показание воздуха считается достоверным. */
public class AirTest {

    private static final long RECEIVED = 1_000_000L;

    @Test
    public void airIsReliableOnlyWithinAgeSinceReceipt() {
        /**
         * Раздел 2 спецификации: возраст считается от получения приложением.
         * Просроченное, не полученное и полученное «из будущего» (часы
         * elapsedRealtime не идут назад — значит, ошибка вызывающего) —
         * недостоверны.
         */
        assertEquals(Double.valueOf(12.5),
                Air.reliable(12.5, RECEIVED, RECEIVED + Air.MAX_AGE_MS));
        assertNull(Air.reliable(12.5, RECEIVED, RECEIVED + Air.MAX_AGE_MS + 1));
        assertNull(Air.reliable(12.5, null, RECEIVED));
        assertNull(Air.reliable(null, RECEIVED, RECEIVED));
        assertNull(Air.reliable(12.5, RECEIVED, RECEIVED - 1));
    }

    @Test
    public void cabinAndOutsideAgeIndependently() {
        /**
         * Улица приходит своим чтением и устаревает отдельно: одна просроченная
         * величина не должна утаскивать за собой вторую.
         */
        Air air = new Air(12.5, RECEIVED, -3.0, RECEIVED - Air.MAX_AGE_MS - 1);
        assertEquals(Double.valueOf(12.5), air.cabin(RECEIVED));
        assertNull(air.outside(RECEIVED));
    }

    @Test
    public void nonFiniteReadingsAreMissingEvenWhenFresh() {
        for (double value : new double[] {Double.NaN, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY}) {
            assertNull(Air.reliable(value, RECEIVED, RECEIVED));
        }
    }
}
