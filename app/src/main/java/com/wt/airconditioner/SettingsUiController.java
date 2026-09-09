package com.wt.airconditioner;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Управляет только вкладкой настроек: выбор темы, показ температуры,
 * разрешения и строка обновления.
 *
 * Три остальные вкладки давно живут в своих контроллерах, а настройки
 * оставались в Activity: из-за этого bindPage и paintPage держали для них
 * особый случай вместо такого же вызова, как у соседей.
 */
final class SettingsUiController implements TabController {

    interface Listener {
        /** Выбрана тема: перекрасить надо весь экран, а не одну вкладку. */
        void onThemeSelected(AppTheme theme);

        /** Плашка температуры принадлежит вкладке сидений. */
        void onTemperatureVisibilityChanged();

        /** Включение службы доступности пишется в лог — он на соседней вкладке. */
        void onLog(String message);
    }

    private final Activity activity;
    private final HeatSettings settings;
    private final AppUpdateController updateController;
    private final Listener listener;
    private ThemePalette palette;

    private View page;

    SettingsUiController(Activity activity, HeatSettings settings,
            AppUpdateController updateController, Listener listener, ThemePalette palette) {
        this.activity = activity;
        this.settings = settings;
        this.updateController = updateController;
        this.listener = listener;
        this.palette = palette;
    }

    /** Разовая привязка: слушатели и стартовое состояние, ничего от палитры. */
    @Override
    public void bind(View page) {
        this.page = page;
        Switch showTemperature = page.findViewById(R.id.showTemperature);
        // Слушателя ещё нет, поэтому setChecked никого не дёргает и снимать его
        // на время не нужно: раньше это приходилось делать только потому, что
        // вкладка пересобиралась при каждой смене темы.
        showTemperature.setChecked(settings.showCabinTemperature());
        showTemperature.setOnCheckedChangeListener((button, checked) -> {
            settings.setShowCabinTemperature(checked);
            listener.onTemperatureVisibilityChanged();
        });

        page.findViewById(R.id.enableAutostart).setOnClickListener(v -> requestPermissions());
        updateController.bind(page);
    }

    /** Всё, что зависит от темы: витрина тем, переключатель, галочка доступа. */
    @Override
    public void applyTheme(ThemePalette palette) {
        this.palette = palette;
        LinearLayout themes = page.findViewById(R.id.themeSegments);
        themes.removeAllViews();
        for (AppTheme option : AppTheme.values()) {
            themes.addView(themeButton(option));
        }
        Ui.paintSwitch(page.findViewById(R.id.showTemperature), palette);
        renderPermissions();
    }

    /**
     * Один раз за запуск проверяем обновление молча, при первом открытии
     * настроек. При ошибке сети кнопка остаётся и позволяет повторить вручную —
     * повторной автопроверки не будет, её гейт живёт в AppUpdateController.
     */
    @Override
    public void onTabVisible(boolean visible) {
        if (visible) {
            updateController.checkAutomatically();
        }
    }

    /**
     * Галочка разрешений. Зовётся ещё и из onResume — человек мог выдать доступ
     * в системных настройках и вернуться, — а туда мы попадаем раньше, чем
     * ViewPager разложит страницы: до первого bind рисовать нечего.
     */
    void renderPermissions() {
        if (page == null) {
            return;
        }
        boolean granted = AccessibilityToggle.isEnabled(activity);
        ImageView check = page.findViewById(R.id.permissionsGranted);
        check.setVisibility(granted ? View.VISIBLE : View.GONE);
        check.setColorFilter(palette.accent, PorterDuff.Mode.SRC_IN);
        page.findViewById(R.id.enableAutostart).setVisibility(granted ? View.GONE : View.VISIBLE);
    }

    private TextView themeButton(AppTheme option) {
        TextView button = new TextView(activity);
        button.setText(option.title);
        button.setTextSize(16);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(Fonts.regular(activity));
        button.setPadding(Ui.dp(activity, 28), 0, Ui.dp(activity, 28), 0);

        // Каждая кнопка показывает цвет своей темы, а не текущей: это витрина,
        // поэтому палитра берётся по опции, а не берётся поле palette.
        boolean selected = option == settings.theme();
        ThemePalette optionPalette = ThemePalette.of(activity, option);
        button.setBackground(Ui.roundRect(activity, Ui.BUTTON_RADIUS_DP,
                selected ? optionPalette.accent : Color.TRANSPARENT,
                selected ? optionPalette.accent : Color.WHITE));
        button.setTextColor(selected ? optionPalette.textOnAccent : Color.WHITE);

        button.setOnClickListener(v -> listener.onThemeSelected(option));

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                        Ui.dp(activity, 44));
        params.setMarginStart(Ui.dp(activity, 12));
        button.setLayoutParams(params);
        return button;
    }

    private void requestPermissions() {
        if (AccessibilityToggle.enableWithoutUi(activity)) {
            listener.onLog(
                    "замер: служба доступности включена программно (WRITE_SECURE_SETTINGS выдан)");
            renderPermissions();
            return;
        }
        listener.onLog("замер: программно включить не удалось, открываю «Спец. возможности»");
        if (!AccessibilityToggle.openSettings(activity)) {
            Toast.makeText(activity, "Экран «Спец. возможности» не открылся — "
                    + "включите службу AutoHeat вручную", Toast.LENGTH_LONG).show();
            listener.onLog("ВНИМАНИЕ: экран «Спец. возможности» не открылся");
        }
    }
}
