package com.wt.airconditioner;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Сегментированный переключатель Flutter-версии: пилюля из нескольких кнопок,
 * выбранная залита акцентом, остальные обведены рамкой.
 *
 * Строится кодом, а не в разметке: фон зависит от выбранной темы и от позиции
 * сегмента (скругление только по краям), а таких переключателей на экране
 * четыре с разными наборами пунктов.
 */
final class SegmentedControl {

    interface OnSelected {
        void onSelected(int index);
    }

    private static final int CORNER_DP = 30;
    private static final int STROKE_DP = 1;

    private SegmentedControl() {
    }

    static void build(LinearLayout container, String[] titles, int selectedIndex,
            int accentColor, OnSelected listener) {
        build(container, titles, selectedIndex, accentColor, 21, listener);
    }

    static void build(LinearLayout container, String[] titles, int selectedIndex,
            int accentColor, int textSizeSp, OnSelected listener) {
        Context context = container.getContext();
        container.removeAllViews();

        for (int index = 0; index < titles.length; index++) {
            boolean selected = index == selectedIndex;
            TextView segment = new TextView(context);
            segment.setText(titles[index]);
            segment.setGravity(Gravity.CENTER);
            segment.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
            segment.setTextColor(selected ? Palette.textOn(accentColor) : Color.WHITE);
            segment.setTypeface(Fonts.regular(context));
            segment.setPadding(dp(context, 18), dp(context, 10), dp(context, 18), dp(context, 10));
            segment.setBackground(background(context, index, titles.length, selected, accentColor));

            final int position = index;
            segment.setOnClickListener(v -> listener.onSelected(position));
            container.addView(segment);
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
        shape.setColor(selected ? accentColor : Color.TRANSPARENT);
        shape.setStroke(dp(context, STROKE_DP), accentColor);
        return shape;
    }

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
