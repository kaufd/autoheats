package com.wt.airconditioner;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Что пользователь включил: для каких сидений работает автоподогрев.
 *
 * Настройки Flutter-версии не переносятся: они лежат в
 * FlutterSharedPreferences.xml с префиксом «flutter.» у ключей, голова одна, а
 * перенастроить два переключателя руками дешевле любой миграции.
 */
final class HeatSettings {

    private static final String FILE = "autoheat";
    private static final String KEY_AUTO_PREFIX = "auto_";

    private final SharedPreferences preferences;

    HeatSettings(Context context) {
        this.preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    boolean isAutoEnabled(Seat seat) {
        return preferences.getBoolean(KEY_AUTO_PREFIX + seat.name(), false);
    }

    void setAutoEnabled(Seat seat, boolean enabled) {
        preferences.edit().putBoolean(KEY_AUTO_PREFIX + seat.name(), enabled).apply();
    }
}
