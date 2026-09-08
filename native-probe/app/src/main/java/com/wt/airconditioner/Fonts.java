package com.wt.airconditioner;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * NotoSans из Flutter-версии. Шрифт лежит в assets, а не в res/font, потому
 * что res/font требует API 26, а minSdk здесь 23 — и без androidx подложить
 * совместимую загрузку нечем.
 */
final class Fonts {

    private static Typeface regular;
    private static Typeface bold;

    private Fonts() {
    }

    static Typeface regular(Context context) {
        if (regular == null) {
            regular = load(context, "fonts/NotoSans-Regular.ttf");
        }
        return regular;
    }

    static Typeface bold(Context context) {
        if (bold == null) {
            bold = load(context, "fonts/NotoSans-Bold.ttf");
        }
        return bold;
    }

    /** Шрифт — оформление: если он не прочитался, экран должен остаться рабочим. */
    private static Typeface load(Context context, String path) {
        try {
            return Typeface.createFromAsset(context.getAssets(), path);
        } catch (RuntimeException e) {
            return Typeface.DEFAULT;
        }
    }

    /** Проставляет шрифт всему дереву, сохраняя жирность, заданную в разметке. */
    static void applyTo(View root) {
        if (root instanceof TextView) {
            TextView text = (TextView) root;
            boolean isBold = text.getTypeface() != null && text.getTypeface().isBold();
            text.setTypeface(isBold ? bold(root.getContext()) : regular(root.getContext()));
            return;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int index = 0; index < group.getChildCount(); index++) {
                applyTo(group.getChildAt(index));
            }
        }
    }
}
