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

/** Вкладка настроек: выбор темы, показ температуры, разрешения, обновление. */
final class SettingsUiController implements TabController {

    interface Listener {
        /** Перекрасить надо весь экран, а не одну вкладку. */
        void onThemeSelected(AppTheme theme);

        /** Плашка температуры принадлежит вкладке сидений. */
        void onTemperatureVisibilityChanged();

        /** Лог живёт на соседней вкладке. */
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

    @Override
    public void bind(View page) {
        this.page = page;
        Switch showTemperature = page.findViewById(R.id.showTemperature);
        /**
         * Слушателя ещё нет, поэтому setChecked никого не дёргает.
         */
        showTemperature.setChecked(settings.showCabinTemperature());
        showTemperature.setOnCheckedChangeListener((button, checked) -> {
            settings.setShowCabinTemperature(checked);
            listener.onTemperatureVisibilityChanged();
        });

        page.findViewById(R.id.enableAutostart).setOnClickListener(v -> requestPermissions());
        updateController.bind(page);
    }

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

    /** Автопроверка обновления один раз за запуск; её гейт — в AppUpdateController. */
    @Override
    public void onTabVisible(boolean visible) {
        if (visible) {
            updateController.checkAutomatically();
        }
    }

    /**
     * Зовётся ещё и из onResume — доступ могли выдать в системных настройках и
     * вернуться, — а туда мы попадаем раньше, чем ViewPager разложит страницы.
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

        /**
         * Витрина: каждая кнопка показывает цвет своей темы, а не текущей.
         */
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
