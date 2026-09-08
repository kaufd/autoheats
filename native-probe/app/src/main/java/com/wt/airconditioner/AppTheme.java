package com.wt.airconditioner;

/**
 * Три темы Flutter-версии. Тема меняет акцентный цвет и фоновую картинку —
 * больше ничего, поэтому вместо системных стилей хватает перекраски в коде.
 */
enum AppTheme {
    BASE("Зелёная", R.color.accent_green, R.drawable.background_base),
    RED("Красная", R.color.accent_red, R.drawable.background_red),
    WHITE("Светлая", R.color.accent_white, R.drawable.background_white);

    final String title;
    final int accentColorRes;
    final int backgroundRes;

    AppTheme(String title, int accentColorRes, int backgroundRes) {
        this.title = title;
        this.accentColorRes = accentColorRes;
        this.backgroundRes = backgroundRes;
    }
}
