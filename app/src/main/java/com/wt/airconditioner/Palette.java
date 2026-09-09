package com.wt.airconditioner;

import android.graphics.Color;

/**
 * Цвет текста поверх заливки: на светлом фоне чёрный, на остальных белый.
 * Выводится из яркости, чтобы не держать таблицу тема → цвет.
 */
final class Palette {

    /** Порог яркости, выше которого фон считается светлым. */
    private static final double LIGHT_THRESHOLD = 0.6;

    private Palette() {
    }

    static int textOn(int background) {
        double luminance = (0.299 * Color.red(background)
                + 0.587 * Color.green(background)
                + 0.114 * Color.blue(background)) / 255.0;
        return luminance > LIGHT_THRESHOLD ? Color.BLACK : Color.WHITE;
    }
}
