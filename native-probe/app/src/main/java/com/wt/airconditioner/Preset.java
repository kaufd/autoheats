package com.wt.airconditioner;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Сохранённое пользователем расписание подогрева: имя, сиденье, длительности
 * уровней и температура, выше которой запускать не нужно.
 *
 * Хранится текстом, а не JSON: org.json в JVM-тестах — заглушка, которая
 * бросает исключение, и разбор пришлось бы проверять на голове вслепую. Поля
 * разделены U+001F (unit separator) — символом, который невозможно ввести с
 * клавиатуры, поэтому имя пресета не нужно экранировать.
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
     * Разбор того, что человек набрал в форме. null — форма не годится, и
     * пресет сохранять нельзя: пустое имя не найти в списке, а расписание из
     * одних нулей ничего не греет и молча «не работало бы».
     *
     * Пустое поле длительности считается нулём: уровень просто пропускается —
     * это осмысленный пресет («сразу с двойки»), а не ошибка ввода.
     */
    static Preset fromInput(String name, Seat seat, String level3, String level2,
            String level1, String threshold) {
        // Перевод строки в имени разорвал бы запись надвое: RECORD — это \n.
        // С экранной клавиатуры головы его не ввести, но вставка из буфера и
        // подключённая USB-клавиатура — вполне, а цена ошибки в том, что
        // пресет пропадает и утаскивает за собой соседнюю запись.
        String trimmedName = name == null ? "" : name.replace(RECORD, " ").trim();
        if (trimmedName.isEmpty()) {
            return null;
        }
        int minutes3 = minutesOrZero(level3);
        int minutes2 = minutesOrZero(level2);
        int minutes1 = minutesOrZero(level1);
        if (minutes3 + minutes2 + minutes1 <= 0) {
            return null;
        }
        Double celsius = celsiusOrNull(threshold);
        if (celsius == null) {
            return null;
        }
        return new Preset(trimmedName, seat,
                new PresetSettings(celsius, minutes3, minutes2, minutes1));
    }

    private static int minutesOrZero(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Порог по умолчанию — 5 °C, как в Flutter-версии. */
    private static Double celsiusOrNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 5.0;
        }
        try {
            return Double.parseDouble(value.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
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
