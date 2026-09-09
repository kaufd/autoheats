package com.wt.airconditioner;

import android.content.Context;
import android.content.SharedPreferences;

/** Пользовательские настройки: режим каждого сиденья, тема, показ температуры. */
final class HeatSettings {

    /**
     * Общий файл: PresetStore пишет пресеты сюда же. Данные связаны — активный
     * пресет сиденья хранится здесь строкой самого пресета из PresetStore.
     */
    static final String FILE = "autoheat";

    private static final String KEY_MODE_PREFIX = "mode_";
    private static final String KEY_LEVEL_PREFIX = "level_";
    private static final String KEY_THEME = "theme";
    private static final String KEY_SHOW_TEMPERATURE = "show_temperature";
    private static final String KEY_DEBUG = "debug_mode";
    private static final String KEY_ACTIVE_PRESET_PREFIX = "preset_";

    private final SharedPreferences preferences;

    HeatSettings(Context context) {
        this.preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    HeatMode mode(Seat seat) {
        return read(KEY_MODE_PREFIX + seat.name(), HeatMode.MANUAL);
    }

    void setMode(Seat seat, HeatMode mode) {
        preferences.edit().putString(KEY_MODE_PREFIX + seat.name(), mode.name()).apply();
    }

    /**
     * Уровень, который автомобиль **подтвердил**, — а не то, что человек нажал:
     * отклонённая запись сюда не попадает (SeatHeatService.setManualLevel).
     *
     * Это оценка состояния сиденья на случай, когда живого ответа от автомобиля
     * нет: процесс только поднялся или экран открыли до подключения сервиса.
     */
    int manualLevel(Seat seat) {
        return preferences.getInt(KEY_LEVEL_PREFIX + seat.name(), 0);
    }

    void setManualLevel(Seat seat, int level) {
        preferences.edit().putInt(KEY_LEVEL_PREFIX + seat.name(), level).apply();
    }

    /**
     * Последний применённый пресет сиденья — целиком строкой, а не позицией в
     * списке: позиции плывут при удалении соседей.
     */
    String activePreset(Seat seat) {
        return preferences.getString(KEY_ACTIVE_PRESET_PREFIX + seat.name(), null);
    }

    void setActivePreset(Seat seat, String encoded) {
        preferences.edit().putString(KEY_ACTIVE_PRESET_PREFIX + seat.name(), encoded).apply();
    }

    AppTheme theme() {
        return read(KEY_THEME, AppTheme.BASE);
    }

    void setTheme(AppTheme theme) {
        preferences.edit().putString(KEY_THEME, theme.name()).apply();
    }

    /** Скрытый режим: долгое нажатие на температуру открывает вкладку логов. */
    boolean debugMode() {
        return preferences.getBoolean(KEY_DEBUG, false);
    }

    void setDebugMode(boolean enabled) {
        preferences.edit().putBoolean(KEY_DEBUG, enabled).apply();
    }

    boolean showCabinTemperature() {
        return preferences.getBoolean(KEY_SHOW_TEMPERATURE, true);
    }

    void setShowCabinTemperature(boolean show) {
        preferences.edit().putBoolean(KEY_SHOW_TEMPERATURE, show).apply();
    }

    /**
     * Чтение enum по имени. Значение могло остаться от прежней версии
     * приложения, поэтому неизвестное имя — это дефолт, а не падение на старте.
     */
    private <E extends Enum<E>> E read(String key, E fallback) {
        String stored = preferences.getString(key, null);
        if (stored == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(fallback.getDeclaringClass(), stored);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
