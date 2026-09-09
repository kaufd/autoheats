package com.wt.airconditioner;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.TextView;

/** Мелкие операции оформления, общие для всех экранов. */
final class Ui {

    /** Скругление у всех кнопок экрана одинаковое. */
    static final int BUTTON_RADIUS_DP = 30;

    /** Значения android:tag из styles.xml — чем красить найденную кнопку. */
    private static final String BUTTON_TAG = "accentButton";
    private static final String OUTLINE_TAG = "outlineButton";

    /** Размеры переключателя; серый в выключенном состоянии — так задумано. */
    private static final int TRACK_ALPHA = 100;
    private static final int TRACK_WIDTH_DP = 65;
    private static final int TRACK_HEIGHT_DP = 30;
    private static final int THUMB_SIZE_DP = 30;

    private Ui() {
    }

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static int color(Context context, int colorRes) {
        return context.getResources().getColor(colorRes);
    }

    static int temperatureColor(Context context, double celsius) {
        if (celsius <= -5) {
            return color(context, R.color.temp_cold);
        }
        if (celsius <= 5) {
            return color(context, R.color.temp_cool);
        }
        if (celsius <= 25) {
            return color(context, R.color.temp_warm);
        }
        return color(context, R.color.accent_red);
    }

    static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    static GradientDrawable roundRect(Context context, float radiusDp, int fill) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(dp(context, radiusDp));
        shape.setColor(fill);
        return shape;
    }

    /** Обводка везде в один dp, меняется только цвет. */
    static GradientDrawable roundRect(Context context, float radiusDp, int fill, int strokeColor) {
        GradientDrawable shape = roundRect(context, radiusDp, fill);
        shape.setStroke(dp(context, 1), strokeColor);
        return shape;
    }

    static GradientDrawable oval(int fill) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(fill);
        return shape;
    }

    /**
     * Шрифт ставится здесь же: кнопки, построенные кодом, не попадают под
     * Fonts.applyTo — оно проходит по дереву разметки один раз при создании
     * экрана.
     */
    static void paintButton(TextView button, ThemePalette palette) {
        button.setBackground(roundRect(button.getContext(), BUTTON_RADIUS_DP, palette.accent));
        button.setTextColor(palette.textOnAccent);
        button.setTypeface(Fonts.regular(button.getContext()));
    }

    /**
     * Текст белый, а не акцентный: красный акцент на чёрном фоне головы читается
     * плохо, а обводки хватает, чтобы кнопка принадлежала теме.
     */
    static void paintOutlineButton(TextView button, ThemePalette palette) {
        button.setBackground(roundRect(button.getContext(), BUTTON_RADIUS_DP,
                Color.TRANSPARENT, palette.accent));
        button.setTextColor(Color.WHITE);
        button.setTypeface(Fonts.regular(button.getContext()));
    }

    /**
     * Трек и ползунок рисуются свои, потому что тинтом системный Switch не
     * лечится: штатный трек тёмный и полупрозрачный, на чёрном фоне головы он не
     * читается ни в каком цвете — после setTrackTintList на эмуляторе виден один
     * ползунок.
     */
    static void paintSwitch(Switch view, ThemePalette palette) {
        Context context = view.getContext();
        int trackOff = color(context, R.color.system_grey_dark);
        view.setTrackDrawable(switchPart(context,
                blend(palette.accent, TRACK_ALPHA, trackOff), trackOff,
                TRACK_WIDTH_DP, TRACK_HEIGHT_DP, GradientDrawable.RECTANGLE));
        view.setThumbDrawable(switchPart(context,
                palette.accent, color(context, R.color.system_grey),
                THUMB_SIZE_DP, THUMB_SIZE_DP, GradientDrawable.OVAL));
        /**
         * Штатные отступы Switch рассчитаны на его собственные 9-patch: с
         * нашими фигурами они добавляют пустое поле сбоку от трека.
         */
        view.setThumbTextPadding(0);
        view.setSwitchMinWidth(dp(context, TRACK_WIDTH_DP));

        /**
         * Свежему StateListDrawable состояние не передаётся: state приходит из
         * drawableStateChanged(), а после смены темы его никто не вызывает —
         * setChecked() с тем же значением выходит сразу. Без этих двух строк
         * переключатель остаётся серым до первого касания.
         */
        view.refreshDrawableState();
        view.jumpDrawablesToCurrentState();
    }

    /**
     * Акцент с прозрачностью, досчитанный до непрозрачного цвета (в отличие от
     * withAlpha, где альфа остаётся в результате). Фон головы почти чёрный, а
     * красный акцент тёмный: положенный на него полупрозрачный трек
     * пропадал, и переключатель выглядел рабочим только в зелёной теме.
     */
    private static int blend(int foreground, int alpha, int background) {
        float weight = alpha / 255f;
        return Color.rgb(
                Math.round(Color.red(foreground) * weight + Color.red(background) * (1 - weight)),
                Math.round(Color.green(foreground) * weight
                        + Color.green(background) * (1 - weight)),
                Math.round(Color.blue(foreground) * weight + Color.blue(background) * (1 - weight)));
    }

    private static Drawable switchPart(Context context, int checkedColor, int uncheckedColor,
            int widthDp, int heightDp, int shape) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_checked},
                switchShape(context, checkedColor, widthDp, heightDp, shape));
        states.addState(new int[]{}, switchShape(context, uncheckedColor, widthDp, heightDp, shape));
        return states;
    }

    private static GradientDrawable switchShape(Context context, int color,
            int widthDp, int heightDp, int form) {
        GradientDrawable shape = form == GradientDrawable.RECTANGLE
                ? roundRect(context, heightDp / 2f, color)
                : oval(color);
        shape.setSize(dp(context, widthDp), dp(context, heightDp));
        return shape;
    }

    /**
     * Красит все кнопки поддерева — их метит тег из @style/PrimaryButton.
     * Обходом, а не перечислением id: иначе новая кнопка молча осталась бы в
     * цвете прошлой темы.
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
