package com.wt.airconditioner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Какие широковещательные действия поднимают сервис. Проверяется тестом,
 * потому что на эмуляторе vendor-действия MTK не воспроизвести, а ошибка
 * здесь тихая: после перезагрузки головы сервис просто не стартует, и
 * автовыключение подогрева не работает без ручного запуска приложения.
 */
public class BootReceiverTest {

    @Test
    public void acceptsStandardBootCompleted() {
        assertTrue(BootReceiver.isBootAction("android.intent.action.BOOT_COMPLETED"));
    }

    @Test
    public void acceptsMediaTekQuickBootActions() {
        assertTrue(BootReceiver.isBootAction("android.intent.action.ACTION_BOOT_IPO"));
        assertTrue(BootReceiver.isBootAction("android.intent.action.QUICKBOOT_POWERON"));
    }

    @Test
    public void ignoresEverythingElse() {
        assertFalse(BootReceiver.isBootAction(null));
        assertFalse(BootReceiver.isBootAction("android.intent.action.MAIN"));
        assertFalse(BootReceiver.isBootAction(""));
    }
}
