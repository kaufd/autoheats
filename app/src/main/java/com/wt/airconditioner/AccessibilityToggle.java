package com.wt.airconditioner;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

/**
 * Состояние службы доступности и попытка включить её без похода в настройки.
 *
 * Служба нужна для оживления процесса после сна головы (см.
 * HeatAccessibilityService). Штатно её включает только человек — запись в
 * Settings.Secure требует WRITE_SECURE_SETTINGS, а это signature|privileged:
 * ни диалогом, ни вручную такое разрешение не выдаётся, только через adb,
 * системную подпись или /system/priv-app. Доступа к голове ни по одному из
 * путей нет.
 *
 * Попытку всё же делаем один раз: пакет com.wt.airconditioner на этой голове
 * whitelisted в CarService, то есть система относится к нему особым образом, и
 * узнать про WRITE_SECURE_SETTINGS можно только попробовав. Ожидаемый исход —
 * SecurityException и открытие экрана настроек.
 */
final class AccessibilityToggle {

    private AccessibilityToggle() {
    }

    static String componentName(Context context) {
        return new ComponentName(context, HeatAccessibilityService.class).flattenToString();
    }

    static boolean isEnabled(Context context) {
        String enabled = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return contains(enabled, componentName(context));
    }

    /**
     * Разбор системного списка включённых служб: записи через двоеточие, имя
     * компонента — «пакет/класс», причём класс система хранит то полностью, то
     * сокращённо (ведущая точка вместо имени пакета). Ошибка здесь тихая:
     * экран сказал бы «включено» при выключенной службе, и автозапуск после
     * сна молча не работал бы.
     */
    static boolean contains(String enabledServices, String target) {
        if (enabledServices == null || target == null) {
            return false;
        }
        for (String entry : enabledServices.split(":")) {
            if (sameComponent(entry.trim(), target)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameComponent(String left, String right) {
        int leftSlash = left.indexOf('/');
        int rightSlash = right.indexOf('/');
        if (leftSlash < 0 || rightSlash < 0) {
            return false;
        }
        String leftPackage = left.substring(0, leftSlash);
        String rightPackage = right.substring(0, rightSlash);
        return leftPackage.equals(rightPackage)
                && expandClass(leftPackage, left.substring(leftSlash + 1))
                        .equals(expandClass(rightPackage, right.substring(rightSlash + 1)));
    }

    private static String expandClass(String packageName, String className) {
        return className.startsWith(".") ? packageName + className : className;
    }

    /** Дописывает службу в список, не задвоив её и не потеряв чужие. */
    static String appended(String enabledServices, String target) {
        if (contains(enabledServices, target)) {
            return enabledServices;
        }
        return (enabledServices == null || enabledServices.isEmpty())
                ? target
                : enabledServices + ":" + target;
    }

    /**
     * Замер: отдаст ли голова WRITE_SECURE_SETTINGS. true — служба включена без
     * участия человека, false — нужен экран настроек.
     */
    static boolean enableWithoutUi(Context context) {
        ContentResolver resolver = context.getContentResolver();
        try {
            String enabled = Settings.Secure.getString(
                    resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    appended(enabled, componentName(context)));
            Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            return isEnabled(context);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Открывает «Спец. возможности»; false — экрана на этой прошивке нет. */
    static boolean openSettings(Context context) {
        return startFirstAvailable(context,
                new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                new Intent(Settings.ACTION_SETTINGS));
    }

    private static boolean startFirstAvailable(Context context, Intent... candidates) {
        for (Intent intent : candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(intent);
                return true;
            } catch (ActivityNotFoundException ignored) {
                /**
                 * пробуем следующий вариант
                 */
            }
        }
        return false;
    }
}
