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

    /**
     * Собранный сегмент: всё, что нужно, чтобы перекрасить его, не пересобирая.
     * Пилюля из трёх-четырёх кнопок перерисовывалась на каждое изменение уровня
     * — на каждый шаг каскада, каждый тап и каждое чтение уровней из машины, —
     * а это ~40 View, столько же Drawable и повторный инфлейт векторных иконок
     * на слабом железе головы. Меняется же в ней только выбранный индекс.
     */
    private static final class Segment {
        final TextView label;
        final ImageView icon;
        final GradientDrawable body;

        Segment(TextView label, ImageView icon, GradientDrawable body) {
            this.label = label;
            this.icon = icon;
            this.body = body;
        }
    }

    private SegmentedControl() {
    }

    /**
     * Приводит переключатель к нужному виду: собирает его при первом вызове,
     * дальше только перекрашивает. Палитра целиком учитывается перекраской,
     * поэтому смена темы тоже не требует пересборки.
     *
     * Контракт: подписи и иконки у одного контейнера постоянны — меняться может
     * только выбранный индекс. Все три переключателя этого экрана (режимы, уровни,
     * сиденье в редакторе пресетов) собраны из констант. Если понадобится
     * переключатель с меняющимся составом, пересборку надо будет заводить по
     * сравнению items, а не по их количеству.
     */
    static void render(LinearLayout container, Item[] items, int selectedIndex,
            ThemePalette palette, int textSizeSp, int heightDp, int paddingDp,
            OnSelected listener) {
        Segment[] segments = segmentsOf(container);
        if (segments == null || segments.length != items.length) {
            segments = build(container, items, textSizeSp, heightDp, paddingDp, listener);
            container.setTag(segments);
        }
        applySelection(segments, selectedIndex, palette);
    }

    private static Segment[] segmentsOf(LinearLayout container) {
        Object tag = container.getTag();
        return tag instanceof Segment[] ? (Segment[]) tag : null;
    }

    private static void applySelection(Segment[] segments, int selectedIndex,
            ThemePalette palette) {
        for (int index = 0; index < segments.length; index++) {
            boolean selected = index == selectedIndex;
            int textColor = selected ? palette.textOnAccent : Color.WHITE;
            segments[index].body.setColor(selected ? palette.accent : UNSELECTED);
            segments[index].label.setTextColor(textColor);
            if (segments[index].icon != null) {
                segments[index].icon.setColorFilter(textColor, PorterDuff.Mode.SRC_IN);
            }
        }
    }

    private static Segment[] build(LinearLayout container, Item[] items,
            int textSizeSp, int heightDp, int paddingDp, OnSelected listener) {
        Context context = container.getContext();
        container.removeAllViews();
        Segment[] segments = new Segment[items.length];

        for (int index = 0; index < items.length; index++) {
            TextView label = new TextView(context);
            label.setText(items[index].title);
            label.setGravity(Gravity.CENTER);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
            label.setTypeface(Fonts.regular(context));
            label.setSingleLine(true);
            // Без этого шрифт добавляет свои поля сверху и снизу, и подпись в
            // сегменте фиксированной высоты уезжает вниз.
            label.setIncludeFontPadding(false);

            View content = label;
            ImageView icon = null;
            if (items[index].iconRes != 0) {
                // Иконка с подписью — отдельная строка по центру сегмента:
                // compound drawable прижал бы значок к краю, и у короткого
                // слова он оторвался бы от текста.
                LinearLayout pair = new LinearLayout(context);
                pair.setOrientation(LinearLayout.HORIZONTAL);
                pair.setGravity(Gravity.CENTER);

                icon = new ImageView(context);
                icon.setImageResource(items[index].iconRes);
                int size = Ui.dp(context, textSizeSp);
                LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(size, size);
                iconParams.setMarginEnd(Ui.dp(context, 8));
                pair.addView(icon, iconParams);
                pair.addView(label);
                content = pair;
            }

            GradientDrawable body = background(context, index, items.length);
            content.setPadding(Ui.dp(context, paddingDp), 0, Ui.dp(context, paddingDp), 0);
            content.setBackground(withDivider(body, index));

            final int position = index;
            if (listener != null) {
                content.setOnClickListener(v -> listener.onSelected(position));
            }

            // Сегменты равной ширины: в оригинале переключатель — цельная
            // пилюля, и «Авто» занимает столько же, сколько «Вручную».
            container.addView(content, new LinearLayout.LayoutParams(
                    0, Ui.dp(context, heightDp), 1f));
            segments[index] = new Segment(label, icon, body);
        }
        return segments;
    }

    /**
     * Разделитель между сегментами — волосяная линия в один пиксель, как в
     * оригинале. Рисуется подложкой самого сегмента, а не зазором: сквозь
     * зазор просвечивал бы фон, который под переключателем то тёмный, то
     * подсвеченный.
     */
    private static Drawable withDivider(GradientDrawable body, int index) {
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
     * соседние кнопки стыкуются встык, как в оригинале. Форма от палитры не
     * зависит и потому задаётся один раз; заливку ставит applySelection.
     */
    private static GradientDrawable background(Context context, int index, int count) {
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
        return shape;
    }

}
