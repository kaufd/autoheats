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

    /**
     * Переключатель по CustomSwitch из Flutter-версии: трек 65×30, ползунок —
     * круг 30 во всю высоту трека. Включённый трек — акцент с прозрачностью
     * 100, выключенный ползунок серый (systemGrey), выключенный трек тёмно-серый
     * (systemGreyDark). Серый в выключенном состоянии — не потеря темы, а
     * оригинальное поведение.
     */
    private static final int TRACK_ALPHA = 100;
    private static final int TRACK_WIDTH_DP = 65;
    private static final int TRACK_HEIGHT_DP = 30;
    private static final int THUMB_SIZE_DP = 30;

    private Ui() {
    }

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    /**
     * Цвет из ресурсов. Своя однострочная копия этого вызова жила в PresetsPanel
     * и AppDialog, а остальные писали его целиком: пять разных способов сказать
     * одно и то же.
     */
    static int color(Context context, int colorRes) {
        return context.getResources().getColor(colorRes);
    }

    /**
     * Цвет подписи с температурой салона: от синего в мороз до акцента в жару.
     * Правило показа, а не логика подогрева, — потому здесь, рядом с остальным
     * оформлением, а не в TemperatureConstants.
     */
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
     * Системный Switch рисуется дефолтным colorAccent платформы: при смене темы
     * он оставался бирюзовым посреди красного экрана.
     *
     * Тинтом это не лечится — штатный трек тёмный и полупрозрачный, на чёрном
     * фоне головы он не читается ни в каком цвете (проверено на эмуляторе:
     * после setTrackTintList виден один ползунок). Поэтому и трек, и ползунок
     * рисуются свои, как и остальные элементы этого экрана.
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
        // Штатные отступы Switch рассчитаны на его собственные 9-patch: с
        // нашими фигурами они добавляют пустое поле сбоку от трека.
        view.setThumbTextPadding(0);
        view.setSwitchMinWidth(dp(context, TRACK_WIDTH_DP));

        // Свежему StateListDrawable состояние не передаётся: setThumbDrawable
        // только запоминает его и просит перерисовку, а state приходит из
        // drawableStateChanged(). После смены темы его никто не вызывает —
        // setChecked() с тем же значением выходит сразу, — и переключатель
        // оставался серым до первого касания.
        view.refreshDrawableState();
        view.jumpDrawablesToCurrentState();
    }

    /**
     * Акцент, положенный с прозрачностью на непрозрачную подложку. Оригинал
     * рисует включённый трек как primary.withAlpha(100) поверх фона, но фон
     * головы почти чёрный, а красный акцент (#951019) сам по себе тёмный: в
     * сумме трек пропадал, и переключатель выглядел рабочим только в зелёной
     * теме. Подложка — тот же серый, что у выключенного трека, поэтому оттенок
     * темы сохраняется, а видимость больше не зависит от яркости акцента.
     *
     * Отличие от withAlpha: там альфа-канал остаётся в цвете, здесь она
     * досчитывается до непрозрачного результата.
     */
    private static int blend(int foreground, int alpha, int background) {
        float weight = alpha / 255f;
        return Color.rgb(
                Math.round(Color.red(foreground) * weight + Color.red(background) * (1 - weight)),
                Math.round(Color.green(foreground) * weight
                        + Color.green(background) * (1 - weight)),
                Math.round(Color.blue(foreground) * weight + Color.blue(background) * (1 - weight)));
    }

    /** Форма для включённого и выключенного состояния — трек или ползунок. */
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
