package com.wt.airconditioner;

import java.util.Locale;

/**
 * Температура одним видом для экрана и лога. Отдельно от TemperatureConstants:
 * форматирование нужно пробнику, сервису и экрану, а таблица авто-расписаний —
 * только каскаду и уйдёт вместе с ним, когда управление переедет на тепловую
 * модель.
 */
final class Temperature {

    private Temperature() {
    }

    /** Locale.US обязателен: в русской локали разделителем стала бы запятая. */
    static String celsiusText(double celsius) {
        return String.format(Locale.US, "%.1f °C", celsius);
    }
}
