package com.wt.airconditioner;

import static org.junit.Assert.assertFalse;
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
    public void stopsSamplingAfterParkedWindow() {
        long parked = 1_000_000L;
        assertTrue(SeatHeatService.keepsSampling(parked,
                parked + SeatHeatService.PARKED_SAMPLE_MS - 1));
        assertFalse(SeatHeatService.keepsSampling(parked,
                parked + SeatHeatService.PARKED_SAMPLE_MS));
    }
}
