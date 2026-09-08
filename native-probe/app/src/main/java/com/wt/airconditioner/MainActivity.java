package com.wt.airconditioner;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import java.util.Locale;

/**
 * Экран управления. Activity связывает жизненный цикл Android с небольшими
 * контроллерами вкладок; состояние автомобиля живёт в SeatHeatService.
 */
public class MainActivity extends Activity implements SeatHeatService.UiListener {

    private static final int TAB_HEAT = 0;
    private static final int TAB_PRESETS = 1;
    private static final int TAB_SETTINGS = 2;
    private static final int TAB_LOG = 3;

    private final ServiceBindingController.Listener bindingListener =
            new ServiceBindingController.Listener() {
                @Override
                public void onConnected(SeatHeatService service,
                        java.util.List<String> logSnapshot) {
                    logUi.setInitialSnapshot(logSnapshot);
                    heatUi.render();
                }

                @Override
                public void onDisconnected() {
                    heatUi.render();
                }
            };

    private HeatSettings settings;
    private AppTheme theme;
    private int accent;

    private ViewFlipper flipper;
    private TextView[] tabButtons;
    private TextView temperatureView;

    private ServiceBindingController serviceBinding;
    private SeatHeatUiController heatUi;
    private LogUiController logUi;
    private PresetsPanel presetsPanel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        settings = new HeatSettings(this);
        theme = settings.theme();
        accent = getResources().getColor(theme.accentColorRes);
        temperatureView = findViewById(R.id.temperature);
        flipper = findViewById(R.id.flipper);

        serviceBinding = new ServiceBindingController(this, this, bindingListener);
        heatUi = new SeatHeatUiController(this, settings, serviceBinding,
                () -> showTab(TAB_PRESETS), accent);
        logUi = new LogUiController(this, serviceBinding, accent);

        buildTabs();
        buildSettingsTab();
        heatUi.bind();
        logUi.bind();

        presetsPanel = new PresetsPanel(this, new PresetStore(this), accent, preset -> {
            SeatHeatService service = serviceBinding.get();
            if (service != null) {
                service.applyPreset(preset);
                showTab(TAB_HEAT);
                heatUi.render();
            }
        });

        applyTheme();
        Fonts.applyTo(findViewById(android.R.id.content));

        // Скрытый переключатель отладки — тот же жест, что во Flutter-версии.
        findViewById(R.id.temperaturePill).setOnLongClickListener(v -> {
            toggleDebugMode();
            return true;
        });

        serviceBinding.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderPermissions();
    }

    @Override
    protected void onDestroy() {
        serviceBinding.destroy();
        // Сервис намеренно не останавливаем: он должен пережить закрытие
        // экрана, иначе автовыключение по зажиганию перестанет работать.
        super.onDestroy();
    }

    // --- оформление ---

    private void applyTheme() {
        accent = getResources().getColor(theme.accentColorRes);
        findViewById(R.id.background).setBackgroundResource(theme.backgroundRes);
        findViewById(R.id.centerDivider).setBackgroundColor(withAlpha(accent, 70));

        View pill = findViewById(R.id.temperaturePill);
        GradientDrawable pillShape = new GradientDrawable();
        pillShape.setShape(GradientDrawable.RECTANGLE);
        pillShape.setCornerRadius(dp(50));
        pillShape.setColor(withAlpha(accent, 30));
        pillShape.setStroke(dp(1), withAlpha(accent, 100));
        pill.setBackground(pillShape);

        ImageView icon = findViewById(R.id.temperatureIcon);
        icon.setColorFilter(accent, PorterDuff.Mode.SRC_IN);

        renderTabs();
        heatUi.setAccent(accent);
        buildSettingsTab();
        presetsPanel.setAccent(accent);
        logUi.setAccent(accent);
        ((ImageView) findViewById(R.id.injectThermometer))
                .setColorFilter(accent, PorterDuff.Mode.SRC_IN);
        paintButton(findViewById(R.id.enableAutostart));
        paintButton(findViewById(R.id.startCascade));
        paintButton(findViewById(R.id.presetSave));
        paintButton(findViewById(R.id.presetNew));
        paintButton(findViewById(R.id.injectTemp));
        paintButton(findViewById(R.id.readTemp));
        paintButton(findViewById(R.id.copyLog));
        paintButton(findViewById(R.id.clearLog));
        paintButton(findViewById(R.id.restartCar));
    }

    private void paintButton(TextView button) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(dp(30));
        shape.setColor(accent);
        button.setBackground(shape);
        button.setTextColor(Palette.textOn(accent));
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // --- вкладки ---

    private void buildTabs() {
        tabButtons = new TextView[]{
                findViewById(R.id.tabHeat),
                findViewById(R.id.tabPresets),
                findViewById(R.id.tabSettings),
                findViewById(R.id.tabLog),
        };
        for (int index = 0; index < tabButtons.length; index++) {
            final int position = index;
            tabButtons[index].setOnClickListener(v -> showTab(position));
        }
        tabButtons[TAB_LOG].setVisibility(
                settings.debugMode() ? View.VISIBLE : View.GONE);
        showTab(TAB_HEAT);
    }

    private void showTab(int index) {
        flipper.setDisplayedChild(index);
        renderTabs();
        if (index == TAB_LOG) {
            logUi.onTabShown();
        }
    }

    private void renderTabs() {
        if (tabButtons == null) {
            return;
        }
        int current = flipper.getDisplayedChild();
        for (int index = 0; index < tabButtons.length; index++) {
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.RECTANGLE);
            shape.setCornerRadius(dp(30));
            shape.setColor(index == current ? accent : Color.TRANSPARENT);
            tabButtons[index].setBackground(shape);
            tabButtons[index].setTextColor(
                    index == current ? Palette.textOn(accent) : Color.WHITE);
        }
    }

    private void toggleDebugMode() {
        boolean enabled = !settings.debugMode();
        settings.setDebugMode(enabled);
        tabButtons[TAB_LOG].setVisibility(enabled ? View.VISIBLE : View.GONE);
        if (!enabled && flipper.getDisplayedChild() == TAB_LOG) {
            showTab(TAB_HEAT);
        }
        Toast.makeText(this, enabled
                ? "Отладка включена — появилась вкладка «Логи»"
                : "Отладка выключена", Toast.LENGTH_SHORT).show();
    }

    // --- вкладка настроек ---

    private void buildSettingsTab() {
        LinearLayout themes = findViewById(R.id.themeSegments);
        themes.removeAllViews();
        for (AppTheme option : AppTheme.values()) {
            themes.addView(themeButton(option));
        }

        Switch showTemperature = findViewById(R.id.showTemperature);
        showTemperature.setOnCheckedChangeListener(null);
        showTemperature.setChecked(settings.showCabinTemperature());
        showTemperature.setOnCheckedChangeListener((button, checked) -> {
            settings.setShowCabinTemperature(checked);
            applyTemperatureVisibility();
        });
        applyTemperatureVisibility();

        findViewById(R.id.enableAutostart).setOnClickListener(v -> requestPermissions());
        findViewById(R.id.startCascade).setOnClickListener(v -> {
            SeatHeatService service = serviceBinding.get();
            if (service != null) {
                service.startAutoHeatNow();
            }
        });
        renderPermissions();
    }

    private TextView themeButton(AppTheme option) {
        TextView button = new TextView(this);
        button.setText(option.title);
        button.setTextSize(16);
        button.setGravity(android.view.Gravity.CENTER);
        button.setTypeface(Fonts.regular(this));
        button.setPadding(dp(28), 0, dp(28), 0);

        boolean selected = option == theme;
        int accentOfOption = getResources().getColor(option.accentColorRes);
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(dp(30));
        shape.setColor(selected ? accentOfOption : Color.TRANSPARENT);
        shape.setStroke(dp(1), selected ? accentOfOption : Color.WHITE);
        button.setBackground(shape);
        button.setTextColor(selected ? Palette.textOn(accentOfOption) : Color.WHITE);

        button.setOnClickListener(v -> {
            theme = option;
            settings.setTheme(option);
            applyTheme();
        });

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(44));
        params.setMarginStart(dp(12));
        button.setLayoutParams(params);
        return button;
    }

    private void applyTemperatureVisibility() {
        findViewById(R.id.temperaturePill).setVisibility(
                settings.showCabinTemperature() ? View.VISIBLE : View.INVISIBLE);
    }

    private void renderPermissions() {
        boolean granted = AccessibilityToggle.isEnabled(this);
        ImageView check = findViewById(R.id.permissionsGranted);
        check.setVisibility(granted ? View.VISIBLE : View.GONE);
        check.setColorFilter(accent, PorterDuff.Mode.SRC_IN);
        findViewById(R.id.enableAutostart).setVisibility(granted ? View.GONE : View.VISIBLE);
    }

    private void requestPermissions() {
        if (AccessibilityToggle.enableWithoutUi(this)) {
            logUi.log("замер: служба доступности включена программно (WRITE_SECURE_SETTINGS выдан)");
            renderPermissions();
            return;
        }
        logUi.log("замер: программно включить не удалось, открываю «Спец. возможности»");
        if (!AccessibilityToggle.openSettings(this)) {
            Toast.makeText(this, "Экран «Спец. возможности» не открылся — "
                    + "включите службу AutoHeat вручную", Toast.LENGTH_LONG).show();
            logUi.log("ВНИМАНИЕ: экран «Спец. возможности» не открылся");
        }
    }

    // --- SeatHeatService.UiListener ---

    @Override
    public void onLogLine(String line) {
        logUi.onLogLine(line);
    }

    @Override
    public void onHvacReady(boolean ready) {
        // UI намеренно не показывает отдельный индикатор связи.
    }

    @Override
    public void onCabinTemperature(double celsius, Integer raw) {
        runOnUiThread(() -> {
            String text = String.format(Locale.US, "%.1f °C", celsius);
            int color = temperatureColor(celsius);
            temperatureView.setText(text);
            temperatureView.setTextColor(color);

            TextView current = findViewById(R.id.injectCurrent);
            current.setText(text);
            current.setTextColor(color);
        });
    }

    @Override
    public void onSeatLevel(Seat seat, int level) {
        runOnUiThread(() -> heatUi.onSeatLevel(seat, level));
    }

    private int temperatureColor(double celsius) {
        if (celsius <= -5) {
            return getResources().getColor(R.color.temp_cold);
        }
        if (celsius <= 5) {
            return getResources().getColor(R.color.temp_cool);
        }
        if (celsius <= 25) {
            return getResources().getColor(R.color.temp_warm);
        }
        return getResources().getColor(R.color.accent_red);
    }
}
