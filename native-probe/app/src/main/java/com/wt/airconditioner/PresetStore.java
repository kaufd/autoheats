package com.wt.airconditioner;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/** Список пресетов в SharedPreferences. Порядок сохраняется — он же порядок на экране. */
final class PresetStore {

    private static final String FILE = "autoheat";
    private static final String KEY = "presets";

    private final SharedPreferences preferences;

    PresetStore(Context context) {
        this.preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    List<Preset> load() {
        return Preset.decodeAll(preferences.getString(KEY, ""));
    }

    void add(Preset preset) {
        List<Preset> presets = load();
        presets.add(preset);
        save(presets);
    }

    /** Удаление по позиции в списке: имена не уникальны, а порядок на экране — да. */
    void removeAt(int index) {
        List<Preset> presets = load();
        if (index < 0 || index >= presets.size()) {
            return;
        }
        presets.remove(index);
        save(presets);
    }

    private void save(List<Preset> presets) {
        preferences.edit().putString(KEY, Preset.encodeAll(new ArrayList<>(presets))).apply();
    }
}
