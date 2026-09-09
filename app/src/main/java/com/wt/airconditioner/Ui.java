package com.wt.airconditioner;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * Мелкие операции оформления, которые нужны каждому экрану: перевод dp в
 * пиксели, прозрачность акцентного цвета, вид кнопки.
 *
 * Собраны здесь, потому что по одной копии в каждом UI-классе они уже
 * разъезжались: dp() существовал в шести местах, withAlpha() — в трёх.
 * Расхождение в таком коде заметно не в диффе, а на экране головы.
 */
final class Ui {

    /**
     * Скругление кнопок из Flutter-версии: у всех одинаковое. Видно и снаружи —
     * вкладки и кнопки выбора темы рисуются здесь же по форме кнопки, и до
     * этого держали четвёртую и пятую копию числа 30.
     */
    static final int BUTTON_RADIUS_DP = 30;

    /** Значения android:tag из styles.xml — чем красить найденную кнопку. */
    private static final String BUTTON_TAG = "accentButton";
    private static final String OUTLINE_TAG = "outlineButton";

    private Ui() {
    }

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    /** Тот же цвет с другой прозрачностью — для подложек и рамок. */
    static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    /**
     * Скруглённый прямоугольник — форма почти всего, что этот экран рисует
     * кодом: кнопок, чипов, бейджей, карточек и панелей. Собиралась она в
     * полутора десятках мест одними и теми же четырьмя строками, и радиусы у
     * одной и той же роли уже начали расходиться.
     */
    static GradientDrawable roundRect(Context context, float radiusDp, int fill) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(dp(context, radiusDp));
        shape.setColor(fill);
        return shape;
    }

    /** То же с обводкой: она везде в один dp, меняется только цвет. */
    static GradientDrawable roundRect(Context context, float radiusDp, int fill, int strokeColor) {
        GradientDrawable shape = roundRect(context, radiusDp, fill);
        shape.setStroke(dp(context, 1), strokeColor);
        return shape;
    }

    /** Круг: точки уровня и ползунки слайдеров. */
    static GradientDrawable oval(int fill) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(fill);
        return shape;
    }

    /**
     * Заливка акцентом, скруглённые углы, контрастный текст. Шрифт ставится
     * здесь же: кнопки, построенные кодом, не попадают под Fonts.applyTo,
     * которое проходит по дереву разметки один раз при создании экрана.
     */
    static void paintButton(TextView button, ThemePalette palette) {
        button.setBackground(roundRect(button.getContext(), BUTTON_RADIUS_DP, palette.accent));
        button.setTextColor(palette.textOnAccent);
        button.setTypeface(Fonts.regular(button.getContext()));
    }

    /**
     * Кнопка без заливки: прозрачный фон, обводка акцентом, белый текст. Белый,
     * а не акцентный: красный акцент #951019 на чёрном фоне головы читается
     * плохо, а обводки хватает, чтобы кнопка принадлежала теме.
     */
    static void paintOutlineButton(TextView button, ThemePalette palette) {
        button.setBackground(roundRect(button.getContext(), BUTTON_RADIUS_DP,
                Color.TRANSPARENT, palette.accent));
        button.setTextColor(Color.WHITE);
        button.setTypeface(Fonts.regular(button.getContext()));
    }

    /**
     * Красит все кнопки поддерева — их метит тег из @style/PrimaryButton.
     * Обходом, а не перечислением id: список из девяти findViewById жил в
     * MainActivity и молча устаревал бы с каждой новой кнопкой, причём
     * незаметно — кнопка просто оставалась бы в цвете прошлой темы.
     *
     * Тем же приёмом работает Fonts.applyTo: на одном экране без фрагментов
     * обход дерева дешевле любого реестра.
     */
    static void paintButtons(View root, ThemePalette palette) {
        if (root instanceof TextView) {
            if (BUTTON_TAG.equals(root.getTag())) {
                paintButton((TextView) root, palette);
                return;
            }
            if (OUTLINE_TAG.equals(root.getTag())) {
                paintOutlineButton((TextView) root, palette);
                return;
            }
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int index = 0; index < group.getChildCount(); index++) {
                paintButtons(group.getChildAt(index), palette);
            }
        }
    }
}
