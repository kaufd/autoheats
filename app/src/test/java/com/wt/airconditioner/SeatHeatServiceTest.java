package com.wt.airconditioner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Когда ряд калибровки встаёт на паузу. Сам сервис на JVM не поднять, поэтому
 * проверяется только решение.
 */
public class SeatHeatServiceTest {

    @Test
    public void keepsSamplingWhileIgnitionIsOnOrUnknown() {
        /**
         * null — и ON, и «зажигание не известно»: без подписки на него ряд
         * иначе встал бы посреди поездки.
         */
        assertTrue(SeatHeatService.keepsSampling(null, 1_000_000L));
    }

    @Test
    public void airIsReliableOnlyWithinAgeSinceReceipt() {
        /**
         * Раздел 2 спецификации: возраст считается от получения приложением.
         * Просроченное, не полученное и полученное «из будущего» (часы
         * elapsedRealtime не идут назад — значит, ошибка вызывающего) —
         * недостоверны.
         */
        long received = 1_000_000L;
        assertEquals(Double.valueOf(12.5), SeatHeatService.reliable(12.5, received,
                received + SeatHeatService.MAX_AIR_AGE_MS));
        assertNull(SeatHeatService.reliable(12.5, received,
                received + SeatHeatService.MAX_AIR_AGE_MS + 1));
        assertNull(SeatHeatService.reliable(12.5, null, received));
        assertNull(SeatHeatService.reliable(null, received, received));
        assertNull(SeatHeatService.reliable(12.5, received, received - 1));
    }

    @Test
    public void stopsSamplingAfterParkedWindow() {
        long parked = 1_000_000L;
        assertTrue(SeatHeatService.keepsSampling(parked,
                parked + SeatHeatService.PARKED_SAMPLE_MS - 1));
        assertFalse(SeatHeatService.keepsSampling(parked,
                parked + SeatHeatService.PARKED_SAMPLE_MS));
    }
}
