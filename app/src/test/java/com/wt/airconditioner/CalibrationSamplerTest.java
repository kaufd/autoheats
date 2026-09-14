package com.wt.airconditioner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Когда ряд калибровки встаёт на паузу. Сам сэмплер на JVM не поднять (Handler
 * и Context), поэтому проверяется только решение.
 */
public class CalibrationSamplerTest {

    @Test
    public void keepsSamplingWhileIgnitionIsOnOrUnknown() {
        /**
         * null — и ON, и «зажигание не известно»: без подписки на него ряд
         * иначе встал бы посреди поездки.
         */
        assertTrue(CalibrationSampler.keepsSampling(null, 1_000_000L));
    }

    @Test
    public void stopsSamplingAfterParkedWindow() {
        long parked = 1_000_000L;
        assertTrue(CalibrationSampler.keepsSampling(parked,
                parked + CalibrationSampler.PARKED_SAMPLE_MS - 1));
        assertFalse(CalibrationSampler.keepsSampling(parked,
                parked + CalibrationSampler.PARKED_SAMPLE_MS));
    }
}
