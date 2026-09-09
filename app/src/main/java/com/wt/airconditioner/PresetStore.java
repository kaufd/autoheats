package com.wt.airconditioner;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Список пресетов в SharedPreferences. Порядок сохраняется — он же порядок на экране. */
final class PresetStore {

    private static final String KEY = "presets";

    private final SharedPreferences preferences;

    PresetStore(Context context) {
        // Тот же файл, что у HeatSettings, — см. HeatSettings.FILE.
        this.preferences = context.getSharedPreferences(HeatSettings.FILE, Context.MODE_PRIVATE);
    }

    /**
     * Список без точных дубликатов. Отсев здесь, а не только в add(): записи,
     * сохранённые до появления запрета, уже лежат в хранилище, и на них
     * адресация по encode() неоднозначна — правка и удаление попадали бы в
     * первую из одинаковых. Список чинится сам при первом же сохранении.
     */
    List<Preset> load() {
        List<Preset> presets = Preset.decodeAll(preferences.getString(KEY, ""));
        Set<String> seen = new HashSet<>();
        List<Preset> unique = new ArrayList<>(presets.size());
        for (Preset preset : presets) {
            if (seen.add(preset.encode())) {
                unique.add(preset);
            }
        }
        return unique;
    }

    /**
     * Ищет пресет по его же строковому представлению. Удалённый пресет не
     * найдётся — и сиденье честно отправит человека выбирать заново.
     */
    Preset find(String encoded) {
        if (encoded == null) {
            return null;
        }
        for (Preset preset : load()) {
            if (preset.encode().equals(encoded)) {
                return preset;
            }
        }
        return null;
    }

    /**
     * false — точно такая запись уже есть. Отказ, а не второй экземпляр: два
     * пресета, совпадающие во всех полях, человек на экране не различит, а
     * адресация по encode() перестала бы быть однозначной — правка или
     * удаление попали бы в первый попавшийся из них.
     */
    boolean add(Preset preset) {
        List<Preset> presets = load();
        String encoded = preset.encode();
        for (Preset existing : presets) {
            if (existing.encode().equals(encoded)) {
                return false;
            }
        }
        presets.add(preset);
        save(presets);
        return true;
    }

    /**
     * Заменяет пресет, который выглядит ровно как `encoded`, сохраняя его место
     * в списке. false — такого больше нет (успели удалить), и вызывающий сам
     * решает, добавлять ли новый.
     *
     * Адресация строкой, а не индексом: между открытием редактора и
     * сохранением список могли перебрать, и индекс указал бы на чужую запись.
     * Однозначность обеспечивает add(): двух одинаковых записей не бывает.
     */
    boolean replace(String encoded, Preset preset) {
        if (encoded == null) {
            return false;
        }
        List<Preset> presets = load();
        for (int index = 0; index < presets.size(); index++) {
            if (presets.get(index).encode().equals(encoded)) {
                presets.set(index, preset);
                save(presets);
                return true;
            }
        }
        return false;
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
