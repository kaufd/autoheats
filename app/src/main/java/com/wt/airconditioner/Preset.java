package com.wt.airconditioner;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Сохранённое расписание подогрева: имя, сиденье, длительности уровней и
 * температура, выше которой запускать не нужно.
 *
 * Текстом, а не JSON: org.json в JVM-тестах — заглушка, которая бросает
 * исключение, и разбор пришлось бы проверять на голове вслепую. Поля разделены
 * U+001F, поэтому имя пресета не нужно экранировать.
 */
final class Preset {

    /** U+001F, unit separator: с клавиатуры головы такой символ не ввести. */
    private static final String FIELD = "\u001F";
    private static final String RECORD = "\n";

    final String name;
    final Seat seat;
    final PresetSettings settings;

    Preset(String name, Seat seat, PresetSettings settings) {
        this.name = name;
        this.seat = seat;
        this.settings = settings;
    }

    String encode() {
        return name + FIELD + seat.name()
                + FIELD + settings.sequence.level3Minutes
                + FIELD + settings.sequence.level2Minutes
                + FIELD + settings.sequence.level1Minutes
                + FIELD + settings.thresholdCelsius;
    }

    /** null — запись битая; такие молча пропускаем, а не роняем весь список. */
    static Preset decode(String line) {
        if (line == null) {
            return null;
        }
        String[] parts = line.split(FIELD, -1);
        if (parts.length != 6) {
            return null;
        }
        try {
            Seat seat = Seat.valueOf(parts[1]);
            return new Preset(parts[0], seat, new PresetSettings(
                    Double.parseDouble(parts[5]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]),
                    Integer.parseInt(parts[4])));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Сборка пресета из формы редактора. null — форма не годится: пустое имя не
     * найти в списке, а расписание из одних нулей ничего не греет.
     *
     * Нулевая длительность уровня — не ошибка, а осмысленный пресет («сразу с
     * двойки»); отвергается только расписание, где нули везде.
     */
    static Preset fromInput(String name, Seat seat, int level3, int level2,
            int level1, double threshold) {
        /**
         * Перевод строки разорвал бы запись надвое: RECORD — это \n. С экранной
         * клавиатуры его не ввести, но вставка из буфера и USB-клавиатура —
         * вполне, а пресет при этом утащит за собой соседнюю запись.
         */
        String trimmedName = name == null ? "" : name.replace(RECORD, " ").trim();
        if (trimmedName.isEmpty()) {
            return null;
        }
        if (level3 + level2 + level1 <= 0) {
            return null;
        }
        return new Preset(trimmedName, seat,
                new PresetSettings(threshold, level3, level2, level1));
    }

    static String encodeAll(List<Preset> presets) {
        StringBuilder text = new StringBuilder();
        for (Preset preset : presets) {
            if (text.length() > 0) {
                text.append(RECORD);
            }
            text.append(preset.encode());
        }
        return text.toString();
    }

    static List<Preset> decodeAll(String text) {
        List<Preset> presets = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return presets;
        }
        for (String line : text.split(RECORD)) {
            Preset preset = decode(line);
            if (preset != null) {
                presets.add(preset);
            }
        }
        return presets;
    }
}
