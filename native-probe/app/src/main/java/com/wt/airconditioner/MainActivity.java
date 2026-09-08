package com.wt.airconditioner;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Экран управления — порт Flutter-версии: шапка с вкладками, фон под выбранную
 * тему, две зеркальные половины салона с изображениями сидений.
 *
 * Соединением с автомобилем владеет SeatHeatService, Activity только показывает
 * его состояние и передаёт команды: закрытие экрана не должно рвать связь.
 */
public class MainActivity extends Activity implements SeatHeatService.UiListener {

    private static final int TAB_HEAT = 0;
    private static final int TAB_PRESETS = 1;
    private static final int TAB_SETTINGS = 2;
    private static final int TAB_LOG = 3;

    /** Уровни в переключателе — как во Flutter: 1, 2, 3 и OFF последним. */
    private static final int[] LEVEL_ORDER = {1, 2, 3, 0};
    private static final String[] LEVEL_TITLES = {"1", "2", "3", "OFF"};

    /**
     * Насколько лог экрана может перерасти буфер сервиса, прежде чем его
     * подрежут. Без запаса каждая строка сверх лимита требовала бы полной
     * перерисовки TextView; с запасом это раз в сотню строк.
     */
    private static final int LOG_TRIM_SLACK = 100;

    private final Deque<String> logLines = new ArrayDeque<>();
    private final Map<Seat, Integer> levels = new EnumMap<>(Seat.class);

    private HeatSettings settings;
    private AppTheme theme;
    private int accent;

    private ViewFlipper flipper;
    private TextView[] tabButtons;
    private TextView temperatureView;
    private TextView statusView;
    private TextView autostartView;
    private TextView logView;
    private ScrollView logScroll;

    private SeatHeatService service;
    private boolean bound;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((SeatHeatService.LocalBinder) binder).getService();
            // Лог сервиса мог начаться до открытия экрана; setUiListener
            // отдаёт его вместе с подпиской, чтобы ничего не потерялось и не
            // задвоилось.
            logLines.clear();
            logLines.addAll(service.setUiListener(MainActivity.this));
            renderLog();
            renderSeats();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        settings = new HeatSettings(this);
        theme = settings.theme();
        accent = getResources().getColor(theme.accentColorRes);

        temperatureView = findViewById(R.id.temperature);
        statusView = findViewById(R.id.status);
        autostartView = findViewById(R.id.autostart);
        logView = findViewById(R.id.log);
        logScroll = findViewById(R.id.logScroll);
        flipper = findViewById(R.id.flipper);

        buildTabs();
        buildSettingsTab();
        buildLogTab();
        applyTheme();
        Fonts.applyTo(findViewById(android.R.id.content));

        // Скрытый переключатель отладки — тот же жест, что во Flutter-версии.
        findViewById(R.id.temperaturePill).setOnLongClickListener(v -> {
            toggleDebugMode();
            return true;
        });

        findViewById(R.id.driverSeat).setOnClickListener(v -> toggleSeat(Seat.DRIVER));
        findViewById(R.id.passengerSeat).setOnClickListener(v -> toggleSeat(Seat.PASSENGER));

        new PresetsPanel(this, new PresetStore(this), accent, preset -> {
            if (service != null) {
                service.applyPreset(preset);
                showTab(TAB_HEAT);
                renderSeats();
            }
        });

        Intent intent = new Intent(this, SeatHeatService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        bound = bindService(intent, connection, 0);
        if (!bound) {
            statusView.setText("Сервис не привязался");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Службу доступности включают на чужом экране, вернуться оттуда можно
        // только сюда — здесь и перечитываем её состояние.
        renderAutostart();
    }

    @Override
    protected void onDestroy() {
        if (service != null) {
            service.setUiListener(null);
        }
        if (bound) {
            unbindService(connection);
            bound = false;
        }
        // Сервис намеренно не останавливаем: он должен пережить закрытие
        // экрана, иначе автовыключение по зажиганию перестанет работать.
        super.onDestroy();
    }

    // --- оформление ---

    /**
     * Тема меняет ровно две вещи — акцентный цвет и фоновую картинку, — поэтому
     * перекрашиваем виджеты на месте вместо пересоздания Activity.
     */
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
        renderSeats();
        buildSettingsTab();
        for (int id : new int[]{R.id.enableAutostart, R.id.startCascade, R.id.presetSave,
                R.id.injectTemp, R.id.readTemp, R.id.copyLog, R.id.restartCar}) {
            paintButton(findViewById(id));
        }
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
            scrollLogToBottom();
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

    // --- главная вкладка ---

    /** Перерисовывает переключатели режима и уровня и индикаторы под сиденьями. */
    private void renderSeats() {
        renderSeat(Seat.DRIVER, R.id.driverModes, R.id.driverLevels, R.id.driverDots);
        renderSeat(Seat.PASSENGER, R.id.passengerModes, R.id.passengerLevels, R.id.passengerDots);
    }

    private void renderSeat(Seat seat, int modesId, int levelsId, int dotsId) {
        HeatMode mode = service == null ? settings.mode(seat) : service.modeOf(seat);
        String[] modeTitles = {HeatMode.MANUAL.title, HeatMode.PRESETS.title, HeatMode.AUTO.title};

        SegmentedControl.build(findViewById(modesId), modeTitles, mode.ordinal(), accent, 17,
                index -> selectMode(seat, HeatMode.values()[index]));

        int level = levelOf(seat);
        int selected = -1;
        for (int i = 0; i < LEVEL_ORDER.length; i++) {
            if (LEVEL_ORDER[i] == level) {
                selected = i;
            }
        }
        LinearLayout levelsRow = findViewById(levelsId);
        SegmentedControl.build(levelsRow, LEVEL_TITLES, selected, accent, 17,
                index -> setLevel(seat, LEVEL_ORDER[index]));
        // Уровнями управляют вручную; в остальных режимах уровень выбирает
        // приложение, и кнопки только показывают, что сейчас происходит.
        levelsRow.setVisibility(mode == HeatMode.MANUAL ? View.VISIBLE : View.INVISIBLE);

        renderDots(findViewById(dotsId), level);
    }

    /** Три кружка под сиденьем: горят те, что ниже или равны текущему уровню. */
    private void renderDots(LinearLayout container, int level) {
        container.removeAllViews();
        for (int index = 0; index < 3; index++) {
            View dot = new View(this);
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(level > index ? accent : getResources().getColor(R.color.system_grey));
            dot.setBackground(shape);

            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(dp(20), dp(20));
            params.setMarginStart(dp(4));
            params.setMarginEnd(dp(4));
            container.addView(dot, params);
        }
    }

    private int levelOf(Seat seat) {
        Integer known = levels.get(seat);
        if (known != null) {
            return known;
        }
        return service == null ? settings.manualLevel(seat) : service.levelOf(seat);
    }

    private void selectMode(Seat seat, HeatMode mode) {
        if (service == null) {
            return;
        }
        if (mode == HeatMode.PRESETS) {
            // Как во Flutter-версии: сегмент «Пресеты» ведёт на вкладку выбора,
            // потому что без выбранного расписания включать нечего.
            showTab(TAB_PRESETS);
            return;
        }
        service.setMode(seat, mode);
        renderSeats();
    }

    private void setLevel(Seat seat, int level) {
        if (service == null) {
            return;
        }
        service.setManualLevel(seat, level);
        renderSeats();
    }

    /**
     * Тап по сиденью перебирает уровни по кругу, а из авто- и пресет-режима
     * возвращает к ручному управлению на единице — так же, как toggleHeatLevel
     * во Flutter-версии.
     */
    private void toggleSeat(Seat seat) {
        if (service == null) {
            return;
        }
        if (service.modeOf(seat) != HeatMode.MANUAL) {
            service.setMode(seat, HeatMode.MANUAL);
            service.setManualLevel(seat, 1);
        } else {
            int level = levelOf(seat);
            service.setManualLevel(seat, level >= 3 ? 0 : level + 1);
        }
        renderSeats();
    }

    // --- вкладка настроек ---

    private void buildSettingsTab() {
        String[] themeTitles = new String[AppTheme.values().length];
        for (int index = 0; index < themeTitles.length; index++) {
            themeTitles[index] = AppTheme.values()[index].title;
        }
        SegmentedControl.build(findViewById(R.id.themeSegments), themeTitles,
                theme.ordinal(), accent, 17, index -> {
                    theme = AppTheme.values()[index];
                    settings.setTheme(theme);
                    applyTheme();
                });

        Switch showTemperature = findViewById(R.id.showTemperature);
        showTemperature.setOnCheckedChangeListener(null);
        showTemperature.setChecked(settings.showCabinTemperature());
        showTemperature.setOnCheckedChangeListener((button, checked) -> {
            settings.setShowCabinTemperature(checked);
            applyTemperatureVisibility();
        });
        applyTemperatureVisibility();

        findViewById(R.id.enableAutostart).setOnClickListener(v -> enableAutostart());
        findViewById(R.id.startCascade).setOnClickListener(v -> {
            if (service != null) {
                service.startAutoHeatNow();
            }
        });
        renderAutostart();
    }

    /**
     * Пилюля скрывается, но остаётся кликабельной зоной: иначе из отладки не
     * выйти тем же жестом, которым в неё вошли.
     */
    private void applyTemperatureVisibility() {
        boolean show = settings.showCabinTemperature();
        findViewById(R.id.temperaturePill)
                .setVisibility(show ? View.VISIBLE : View.INVISIBLE);
    }

    private void renderAutostart() {
        if (autostartView == null) {
            return;
        }
        boolean enabled = AccessibilityToggle.isEnabled(this);
        autostartView.setText(enabled
                ? "Служба «AutoHeat» включена — приложение поднимется само"
                : "Служба «AutoHeat» в «Спец. возможностях» выключена: после сна "
                        + "головы подогрев не запустится сам");
        autostartView.setTextColor(enabled
                ? getResources().getColor(R.color.accent_green)
                : getResources().getColor(R.color.temp_warm));
        findViewById(R.id.enableAutostart).setEnabled(!enabled);
    }

    private void enableAutostart() {
        // Сначала пробуем без UI — замер, отдаёт ли голова WRITE_SECURE_SETTINGS
        // whitelisted-пакету. Ожидаемо не отдаёт, тогда ведём человека на экран.
        if (AccessibilityToggle.enableWithoutUi(this)) {
            log("замер: служба доступности включена программно "
                    + "(WRITE_SECURE_SETTINGS выдан)");
            renderAutostart();
            return;
        }
        log("замер: программно включить не удалось, открываю «Спец. возможности»");
        if (!AccessibilityToggle.openSettings(this)) {
            Toast.makeText(this, "Экран «Спец. возможности» не открылся — "
                    + "включите службу AutoHeat вручную", Toast.LENGTH_LONG).show();
            log("ВНИМАНИЕ: экран «Спец. возможности» не открылся");
        }
    }

    // --- вкладка логов ---

    private void buildLogTab() {
        findViewById(R.id.readTemp).setOnClickListener(v -> {
            if (service != null) {
                service.readCabinTemperature();
            }
        });
        findViewById(R.id.copyLog).setOnClickListener(v -> copyLog());
        findViewById(R.id.injectTemp).setOnClickListener(v -> injectTemperature());
        findViewById(R.id.restartCar).setOnClickListener(v -> {
            if (service == null) {
                return;
            }
            // Статус ставим сами: пока новый CarHvacProbe не ответит,
            // готовность неизвестна, и прежнее «подключён» означало бы связь,
            // которой уже нет.
            statusView.setText("Переподключение к автомобилю…");
            service.restartCarConnection();
        });
    }

    private void injectTemperature() {
        EditText field = findViewById(R.id.injectValue);
        String text = field.getText().toString().trim().replace(',', '.');
        if (service == null || text.isEmpty()) {
            return;
        }
        try {
            service.injectTemperature(Double.parseDouble(text));
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Не похоже на температуру", Toast.LENGTH_SHORT).show();
        }
    }

    private void copyLog() {
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("AutoHeat", logText()));
        Toast.makeText(this, "Лог скопирован", Toast.LENGTH_SHORT).show();
    }

    private String logText() {
        StringBuilder text = new StringBuilder();
        for (String line : logLines) {
            text.append(line).append('\n');
        }
        return text.toString();
    }

    private void renderLog() {
        logView.setText(logText());
        scrollLogToBottom();
    }

    private void scrollLogToBottom() {
        logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void log(String message) {
        if (service != null) {
            service.onLog(message);
        }
    }

    // --- SeatHeatService.UiListener (приходит с потока сервиса) ---

    @Override
    public void onLogLine(String line) {
        runOnUiThread(() -> {
            logLines.addLast(line);
            // Сервис держит 500 строк — экран не должен расти дальше:
            // за долгую поездку поток температурных событий иначе превратит
            // каждую строку в перерисовку всё более длинного текста.
            if (logLines.size() > SeatHeatService.LOG_CAPACITY + LOG_TRIM_SLACK) {
                while (logLines.size() > SeatHeatService.LOG_CAPACITY) {
                    logLines.removeFirst();
                }
                renderLog();
            } else {
                logView.append(line + "\n");
                scrollLogToBottom();
            }
        });
    }

    @Override
    public void onHvacReady(boolean ready) {
        runOnUiThread(() -> statusView.setText(
                ready ? "" : "Нет связи с автомобилем"));
    }

    @Override
    public void onCabinTemperature(double celsius, int raw) {
        runOnUiThread(() -> {
            temperatureView.setText(String.format(Locale.US, "%.1f °C", celsius));
            temperatureView.setTextColor(temperatureColor(celsius));
        });
    }

    @Override
    public void onSeatLevel(Seat seat, int level) {
        runOnUiThread(() -> {
            levels.put(seat, level);
            renderSeats();
        });
    }

    /** Цвет значения по диапазонам — как в CabinTemperatureDisplay. */
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
