package com.wt.airconditioner;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;

/**
 * Единственная задача — поднять SeatHeatService, когда система сама поднимет
 * этот процесс.
 *
 * Голова гасит экран через 10 минут после пропадания ACC и уходит в сон,
 * выгружая приложения; при пробуждении BOOT_COMPLETED не приходит — это не
 * загрузка, — поэтому BootReceiver покрывает только длинную стоянку. Службу
 * доступности система биндит сама, и вместе с ней поднимается процесс: это
 * единственный доступный способ пережить магазин и заправку. Ровно так же
 * автозапуск решён в приложениях сообщества и во Flutter-версии AutoHeat.
 *
 * Ничего тяжелее запуска сервиса здесь быть не должно: класс работает в
 * системном пути и его отказ выглядел бы как отвалившийся автозапуск.
 */
public class HeatAccessibilityService extends AccessibilityService {

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Intent service = new Intent(this, SeatHeatService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(service);
        } else {
            startService(service);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // События не нужны: интерес только в том, что система нас забиндила.
    }

    @Override
    public void onInterrupt() {
    }
}
