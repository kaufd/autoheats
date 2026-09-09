package com.wt.airconditioner;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
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

    private final PresetsPanel.Listener presetListener = new PresetsPanel.Listener() {
        @Override
        public void onApply(Preset preset) {
            SeatHeatService service = serviceBinding.get();
            if (service != null) {
                service.applyPreset(preset);
                showTab(TAB_HEAT);
                heatUi.render();
            }
        }

        @Override
        public void onPresetChanged(String oldEncoded, String newEncoded) {
            SeatHeatService service = serviceBinding.get();
            if (service != null) {
                service.onPresetChanged(oldEncoded, newEncoded);
            }
        }
    };

    private HeatSettings settings;
    private AppTheme theme;
    private ThemePalette palette;

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
        palette = ThemePalette.of(this, theme);
        temperatureView = findViewById(R.id.temperature);
        flipper = findViewById(R.id.flipper);

        serviceBinding = new ServiceBindingController(this, this, bindingListener);
        heatUi = new SeatHeatUiController(this, settings, serviceBinding,
                () -> showTab(TAB_PRESETS), palette);
        logUi = new LogUiController(this, serviceBinding, palette);

        buildTabs();
        buildSettingsTab();
        heatUi.bind();
        logUi.bind();

        presetsPanel = new PresetsPanel(this, new PresetStore(this), palette, presetListener);

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

    /**
     * Единственная точка смены оформления: палитра собирается один раз и уходит
     * всем, кто рисует. Кнопки не перечисляются поимённо — их находит обход
     * дерева по тегу из @style/PrimaryButton, поэтому новая кнопка в разметке
     * перекрашивается сама.
     */
    private void applyTheme() {
        palette = ThemePalette.of(this, theme);
        findViewById(R.id.background).setBackgroundResource(palette.backgroundRes);
        findViewById(R.id.centerDivider).setBackgroundColor(palette.divider);

        View pill = findViewById(R.id.temperaturePill);
        GradientDrawable pillShape = new GradientDrawable();
        pillShape.setShape(GradientDrawable.RECTANGLE);
        pillShape.setCornerRadius(Ui.dp(this, 50));
        // Плашка температуры бледнее чипов: своя пара значений, и она здесь
        // единственная — роли в ThemePalette заведены только для повторяющихся.
        pillShape.setColor(Ui.withAlpha(palette.accent, 30));
        pillShape.setStroke(Ui.dp(this, 1), Ui.withAlpha(palette.accent, 100));
        pill.setBackground(pillShape);

        ImageView icon = findViewById(R.id.temperatureIcon);
        icon.setColorFilter(palette.accent, PorterDuff.Mode.SRC_IN);
        ((ImageView) findViewById(R.id.injectThermometer))
                .setColorFilter(palette.accent, PorterDuff.Mode.SRC_IN);

        renderTabs();
        buildSettingsTab();
        heatUi.applyTheme(palette);
        presetsPanel.applyTheme(palette);
        logUi.applyTheme(palette);
        Ui.paintButtons(findViewById(android.R.id.content), palette);
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
    private void paintSwitch(Switch view) {
        int trackOff = getResources().getColor(R.color.system_grey_dark);
        view.setTrackDrawable(switchPart(
                blend(palette.accent, TRACK_ALPHA, trackOff), trackOff,
                TRACK_WIDTH_DP, TRACK_HEIGHT_DP, GradientDrawable.RECTANGLE));
        view.setThumbDrawable(switchPart(
                palette.accent, getResources().getColor(R.color.system_grey),
                THUMB_SIZE_DP, THUMB_SIZE_DP, GradientDrawable.OVAL));
        // Штатные отступы Switch рассчитаны на его собственные 9-patch: с
        // нашими фигурами они добавляют пустое поле сбоку от трека.
        view.setThumbTextPadding(0);
        view.setSwitchMinWidth(Ui.dp(this, TRACK_WIDTH_DP));

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
    private Drawable switchPart(int checkedColor, int uncheckedColor,
            int widthDp, int heightDp, int shape) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_checked},
                switchShape(checkedColor, widthDp, heightDp, shape));
        states.addState(new int[]{}, switchShape(uncheckedColor, widthDp, heightDp, shape));
        return states;
    }

    private GradientDrawable switchShape(int color, int widthDp, int heightDp, int form) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(form);
        if (form == GradientDrawable.RECTANGLE) {
            shape.setCornerRadius(Ui.dp(this, heightDp / 2f));
        }
        shape.setColor(color);
        shape.setSize(Ui.dp(this, widthDp), Ui.dp(this, heightDp));
        return shape;
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
            shape.setCornerRadius(Ui.dp(this, 30));
            shape.setColor(index == current ? palette.accent : Color.TRANSPARENT);
            tabButtons[index].setBackground(shape);
            tabButtons[index].setTextColor(
                    index == current ? palette.textOnAccent : Color.WHITE);
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
        paintSwitch(showTemperature);
        showTemperature.setOnCheckedChangeListener(null);
        showTemperature.setChecked(settings.showCabinTemperature());
        showTemperature.setOnCheckedChangeListener((button, checked) -> {
            settings.setShowCabinTemperature(checked);
            applyTemperatureVisibility();
        });
        applyTemperatureVisibility();

        findViewById(R.id.enableAutostart).setOnClickListener(v -> requestPermissions());
        renderPermissions();
    }

    private TextView themeButton(AppTheme option) {
        TextView button = new TextView(this);
        button.setText(option.title);
        button.setTextSize(16);
        button.setGravity(android.view.Gravity.CENTER);
        button.setTypeface(Fonts.regular(this));
        button.setPadding(Ui.dp(this, 28), 0, Ui.dp(this, 28), 0);

        // Каждая кнопка показывает цвет своей темы, а не текущей: это витрина,
        // поэтому палитра берётся по опции, а не берётся поле palette.
        boolean selected = option == theme;
        ThemePalette optionPalette = ThemePalette.of(this, option);
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(Ui.dp(this, 30));
        shape.setColor(selected ? optionPalette.accent : Color.TRANSPARENT);
        shape.setStroke(Ui.dp(this, 1), selected ? optionPalette.accent : Color.WHITE);
        button.setBackground(shape);
        button.setTextColor(selected ? optionPalette.textOnAccent : Color.WHITE);

        button.setOnClickListener(v -> {
            theme = option;
            settings.setTheme(option);
            applyTheme();
        });

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(this, 44));
        params.setMarginStart(Ui.dp(this, 12));
        button.setLayoutParams(params);
        return button;
    }

    /**
     * Скрытие через прозрачность, а не INVISIBLE: на этом же пятне живёт
     * длинный тап, включающий отладку. Невидимая View не получает касаний, и
     * человек, спрятавший температуру, не смог бы выключить вкладку «Логи» —
     * пришлось бы сначала возвращать температуру на экран.
     */
    private void applyTemperatureVisibility() {
        findViewById(R.id.temperaturePill)
                .setAlpha(settings.showCabinTemperature() ? 1f : 0f);
    }

    private void renderPermissions() {
        boolean granted = AccessibilityToggle.isEnabled(this);
        ImageView check = findViewById(R.id.permissionsGranted);
        check.setVisibility(granted ? View.VISIBLE : View.GONE);
        check.setColorFilter(palette.accent, PorterDuff.Mode.SRC_IN);
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
