package com.wt.airconditioner;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Поднимает сервис после перезагрузки головы, чтобы автовыключение подогрева
 * работало без ручного запуска приложения.
 *
 * Кроме штатного BOOT_COMPLETED слушаем vendor-действия MediaTek: на MTK-головах
 * (а голова Changan — MT8666) быстрый старт из псевдо-выключения рассылает
 * ACTION_BOOT_IPO / QUICKBOOT_POWERON вместо BOOT_COMPLETED. Прецедента у нас
 * нет — во Flutter-версии boot-ресивера не было, — а лишний фильтр не стоит
 * ничего и экономит цикл ручной прошивки, если штатное действие не придёт.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String ACTION_BOOT_IPO = "android.intent.action.ACTION_BOOT_IPO";
    private static final String ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !isBootAction(intent.getAction())) {
            return;
        }
        SeatHeatService.start(context);
    }

    /** package-private ради юнит-теста: на эмуляторе vendor-действия не воспроизвести. */
    static boolean isBootAction(String action) {
        return Intent.ACTION_BOOT_COMPLETED.equals(action)
                || ACTION_BOOT_IPO.equals(action)
                || ACTION_QUICKBOOT_POWERON.equals(action);
    }
}
