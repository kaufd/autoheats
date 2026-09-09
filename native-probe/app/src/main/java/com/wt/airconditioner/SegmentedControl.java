package com.wt.airconditioner;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.graphics.PorterDuff;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Сегментированный переключатель Flutter-версии (там это Material 3
 * SegmentedButton): пилюля из нескольких кнопок, выбранная залита акцентом,
 * невыбранные — серые.
 *
 * Размеры и цвета сняты с эталонных скриншотов Flutter-версии на эмуляторе
 * 1920×720: высота 46 у переключателя режима и 29 у переключателя уровня,
 * невыбранный фон #303030, скругление только по краям пилюли.
 */
final class SegmentedControl {

    /** Фон невыбранного сегмента — замерен на скриншоте оригинала. */
    private static final int UNSELECTED = 0xFF303030;
    /** Тонкая линия между сегментами — почти чёрная, как в оригинале. */
    private static final int DIVIDER = 0xFF141414;
    private static final int CORNER_DP = 30;

    interface OnSelected {
        void onSelected(int index);
    }

    /** Пункт переключателя: подпись и необязательная иконка слева от неё. */
    static final class Item {
        final String title;
        final int iconRes;

        Item(String title) {
            this(title, 0);
        }

        Item(String title, int iconRes) {
            this.title = title;
            this.iconRes = iconRes;
        }
    }

    private SegmentedControl() {
    }

    static void build(LinearLayout container, Item[] items, int selectedIndex,
            ThemePalette palette, int textSizeSp, int heightDp, int paddingDp,
            OnSelected listener) {
        Context context = container.getContext();
        container.removeAllViews();

        for (int index = 0; index < items.length; index++) {
            boolean selected = index == selectedIndex;
            int textColor = selected ? palette.textOnAccent : Color.WHITE;

            TextView label = new TextView(context);
            label.setText(items[index].title);
            label.setGravity(Gravity.CENTER);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
            label.setTextColor(textColor);
            label.setTypeface(Fonts.regular(context));
            label.setSingleLine(true);
            // Без этого шрифт добавляет свои поля сверху и снизу, и подпись в
            // сегменте фиксированной высоты уезжает вниз.
            label.setIncludeFontPadding(false);

            View content = label;
            if (items[index].iconRes != 0) {
                // Иконка с подписью — отдельная строка по центру сегмента:
                // compound drawable прижал бы значок к краю, и у короткого
                // слова он оторвался бы от текста.
                LinearLayout pair = new LinearLayout(context);
                pair.setOrientation(LinearLayout.HORIZONTAL);
                pair.setGravity(Gravity.CENTER);

                ImageView icon = new ImageView(context);
                icon.setImageResource(items[index].iconRes);
                icon.setColorFilter(textColor, PorterDuff.Mode.SRC_IN);
                int size = Ui.dp(context, textSizeSp);
                LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(size, size);
                iconParams.setMarginEnd(Ui.dp(context, 8));
                pair.addView(icon, iconParams);
                pair.addView(label);
                content = pair;
            }

            content.setPadding(Ui.dp(context, paddingDp), 0, Ui.dp(context, paddingDp), 0);
            content.setBackground(withDivider(context,
                    background(context, index, items.length, selected, palette), index));

            final int position = index;
            if (listener != null) {
                content.setOnClickListener(v -> listener.onSelected(position));
            }

            // Сегменты равной ширины: в оригинале переключатель — цельная
            // пилюля, и «Авто» занимает столько же, сколько «Вручную».
            container.addView(content, new LinearLayout.LayoutParams(
                    0, Ui.dp(context, heightDp), 1f));
        }
    }

    /**
     * Разделитель между сегментами — волосяная линия в один пиксель, как в
     * оригинале. Рисуется подложкой самого сегмента, а не зазором: сквозь
     * зазор просвечивал бы фон, который под переключателем то тёмный, то
     * подсвеченный.
     */
    private static Drawable withDivider(Context context, GradientDrawable body, int index) {
        if (index == 0) {
            return body;
        }
        LayerDrawable layers = new LayerDrawable(
                new Drawable[]{new ColorDrawable(DIVIDER), body});
        layers.setLayerInset(1, 1, 0, 0, 0);
        return layers;
    }

    /**
     * Скругление только у крайних сегментов — так пилюля выглядит цельной, а
     * соседние кнопки стыкуются встык, как в оригинале.
     */
    private static GradientDrawable background(Context context, int index, int count,
            boolean selected, ThemePalette palette) {
        float corner = Ui.dp(context, CORNER_DP);
        boolean first = index == 0;
        boolean last = index == count - 1;

        float[] radii = {
                first ? corner : 0, first ? corner : 0,
                last ? corner : 0, last ? corner : 0,
                last ? corner : 0, last ? corner : 0,
                first ? corner : 0, first ? corner : 0,
        };

        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadii(radii);
        shape.setColor(selected ? palette.accent : UNSELECTED);
        return shape;
    }

}
