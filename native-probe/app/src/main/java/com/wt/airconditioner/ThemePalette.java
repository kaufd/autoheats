package com.wt.airconditioner;

import android.content.Context;

/**
 * Готовые цвета темы. Раньше по контроллерам ходил голый int accent, и каждый
 * сам вспоминал, какая прозрачность значит «разделитель», а какая «обводка
 * чипа»: 70, 51, 120 и 90 были рассыпаны по четырём классам магическими
 * числами. Разъехаться они могли молча — на экране головы разница в
 * прозрачности заметна не сразу.
 *
 * Здесь только те роли, которые встречаются больше одного раза. Разовые
 * значения (подложка температурной плашки, например) остаются на месте: имя
 * для одного вызова — это словарь ради словаря.
 */
final class ThemePalette {

    /** Разделительная линия между колонками. */
    private static final int DIVIDER_ALPHA = 70;
    /** Заливка чипа, бейджа, пилюли с длительностью. */
    private static final int CHIP_FILL_ALPHA = 51;
    /** Обводка тех же чипов и карточек пресетов. */
    private static final int CHIP_STROKE_ALPHA = 120;
    /** Обводка крупных панелей — лога и инжектора. */
    private static final int PANEL_STROKE_ALPHA = 90;

    final int accent;
    final int backgroundRes;
    final int textOnAccent;
    final int divider;
    final int chipFill;
    final int chipStroke;
    final int panelStroke;

    private ThemePalette(int accent, int backgroundRes) {
        this.accent = accent;
        this.backgroundRes = backgroundRes;
        this.textOnAccent = Palette.textOn(accent);
        this.divider = Ui.withAlpha(accent, DIVIDER_ALPHA);
        this.chipFill = Ui.withAlpha(accent, CHIP_FILL_ALPHA);
        this.chipStroke = Ui.withAlpha(accent, CHIP_STROKE_ALPHA);
        this.panelStroke = Ui.withAlpha(accent, PANEL_STROKE_ALPHA);
    }

    static ThemePalette of(Context context, AppTheme theme) {
        return new ThemePalette(
                context.getResources().getColor(theme.accentColorRes), theme.backgroundRes);
    }
}
