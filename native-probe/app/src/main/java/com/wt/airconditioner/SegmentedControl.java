package com.wt.airconditioner;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
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
            int accentColor, int textSizeSp, int heightDp, int paddingDp,
            OnSelected listener) {
        Context context = container.getContext();
        container.removeAllViews();

        for (int index = 0; index < items.length; index++) {
            boolean selected = index == selectedIndex;
            TextView segment = new TextView(context);
            segment.setText(items[index].title);
            segment.setGravity(Gravity.CENTER);
            segment.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
            segment.setTextColor(selected ? Palette.textOn(accentColor) : Color.WHITE);
            segment.setTypeface(Fonts.regular(context));
            segment.setPadding(dp(context, paddingDp), 0, dp(context, paddingDp), 0);
            segment.setBackground(background(context, index, items.length, selected, accentColor));
            segment.setSingleLine(true);

            if (items[index].iconRes != 0) {
                Drawable icon = context.getResources().getDrawable(items[index].iconRes);
                int size = dp(context, textSizeSp + 3);
                icon.setBounds(0, 0, size, size);
                segment.setCompoundDrawables(icon, null, null, null);
                segment.setCompoundDrawablePadding(dp(context, 6));
            }

            final int position = index;
            if (listener != null) {
                segment.setOnClickListener(v -> listener.onSelected(position));
            }

            // Сегменты равной ширины: в оригинале переключатель — цельная
            // пилюля, и «Авто» занимает столько же, сколько «Вручную».
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, dp(context, heightDp), 1f);
            // Зазора нет: в оригинале сегменты стыкуются вплотную и делятся
            // только цветом заливки.
            container.addView(segment, params);
        }
    }

    /**
     * Скругление только у крайних сегментов — так пилюля выглядит цельной, а
     * соседние кнопки стыкуются встык, как в оригинале.
     */
    private static GradientDrawable background(Context context, int index, int count,
            boolean selected, int accentColor) {
        float corner = dp(context, CORNER_DP);
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
        shape.setColor(selected ? accentColor : UNSELECTED);
        return shape;
    }

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
