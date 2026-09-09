package com.wt.airconditioner;

import android.graphics.Color;

/**
 * Цвет текста поверх заливки. Во Flutter-версии это было прописано в темах
 * руками: у светлой темы текст на выбранной кнопке чёрный, у остальных белый.
 * Здесь то же правило выводится из яркости, чтобы не держать таблицу.
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
