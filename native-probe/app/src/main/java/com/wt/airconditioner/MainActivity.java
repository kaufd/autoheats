package com.wt.airconditioner;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/**
 * Экран управления. Соединением с Car владеет SeatHeatService — Activity
 * только показывает его состояние и передаёт команды, поэтому закрытие экрана
 * не рвёт связь с автомобилем.
 */
public class MainActivity extends Activity implements SeatHeatService.UiListener {

    private static final int[] LEVELS = {0, 1, 2, 3};

    /** Порядок вкладок в ViewFlipper — он же порядок кнопок сверху. */
    private static final int TAB_HEAT = 0;
    private static final int TAB_PRESETS = 1;
    private static final int TAB_LOG = 2;

    /**
     * Насколько лог экрана может перерасти буфер сервиса, прежде чем его
     * подрежут. Без запаса каждая строка сверх лимита требовала бы полной
     * перерисовки TextView; с запасом это раз в сотню строк.
     */
    private static final int LOG_TRIM_SLACK = 100;

    private TextView statusView;
    private TextView temperatureView;
    private TextView autostartView;
    private Button enableAutostartButton;
    private TextView logView;
    private ScrollView logScroll;
    private ViewFlipper flipper;

    private final Deque<String> logLines = new ArrayDeque<>();

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
            renderAutoCheckboxes();
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

        statusView = findViewById(R.id.status);
        temperatureView = findViewById(R.id.temperature);
        autostartView = findViewById(R.id.autostart);
        enableAutostartButton = findViewById(R.id.enableAutostart);
        logView = findViewById(R.id.log);
        logScroll = findViewById(R.id.logScroll);

        enableAutostartButton.setOnClickListener(v -> enableAutostart());

        buildLevelButtons(findViewById(R.id.driverRow), true);
        buildLevelButtons(findViewById(R.id.passengerRow), false);
        buildTabs();

        // Пресеты живут в SharedPreferences и не зависят от связи с сервисом —
        // список показываем сразу, не дожидаясь привязки.
        new PresetsPanel(this, new PresetStore(this), preset -> {
            if (service != null) {
                service.applyPreset(preset);
                showTab(TAB_HEAT);
            }
        });

        findViewById(R.id.readTemp).setOnClickListener(v -> {
            if (service != null) {
                service.readCabinTemperature();
            }
        });
        findViewById(R.id.copyLog).setOnClickListener(v -> copyLog());
        findViewById(R.id.injectTemp).setOnClickListener(v -> injectTemperature());
        findViewById(R.id.startCascade).setOnClickListener(v -> {
            if (service != null) {
                service.startAutoHeatNow();
            }
        });
        findViewById(R.id.restartCar).setOnClickListener(v -> {
            if (service == null) {
                return;
            }
            // Статус ставим сами: пока новый CarHvacProbe не ответит,
            // готовность неизвестна, и прежний зелёный «HVAC подключён»
            // означал бы связь, которой уже нет.
            statusView.setText("Переподключение к Car…");
            statusView.setTextColor(0xFFFFC107);
            service.restartCarConnection();
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
        // Службу включают на чужом экране, вернуться оттуда можно только сюда.
        renderAutostart();
    }

    private void renderAutostart() {
        boolean enabled = AccessibilityToggle.isEnabled(this);
        autostartView.setText(enabled
                ? "Автозапуск после сна: работает"
                : "Автозапуск после сна: ВЫКЛЮЧЕН — служба «AutoHeat» "
                        + "в «Спец. возможностях» не включена");
        autostartView.setTextColor(enabled ? 0xFF8BC34A : 0xFFFFC107);
        enableAutostartButton.setEnabled(!enabled);
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

    private void log(String message) {
        if (service != null) {
            service.onLog(message);
        }
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

    private void buildTabs() {
        flipper = findViewById(R.id.flipper);
        findViewById(R.id.tabHeat).setOnClickListener(v -> showTab(TAB_HEAT));
        findViewById(R.id.tabPresets).setOnClickListener(v -> showTab(TAB_PRESETS));
        findViewById(R.id.tabLog).setOnClickListener(v -> showTab(TAB_LOG));
        showTab(TAB_HEAT);
    }

    private void showTab(int index) {
        flipper.setDisplayedChild(index);
        if (index == TAB_LOG) {
            scrollLogToBottom();
        }
    }

    /**
     * Галочки заполняются только когда сервис привязан: до этого сохранённое
     * состояние неизвестно, а показать «выключено» вместо него значит соврать.
     */
    private void renderAutoCheckboxes() {
        bindAutoCheckbox(findViewById(R.id.autoDriver), Seat.DRIVER);
        bindAutoCheckbox(findViewById(R.id.autoPassenger), Seat.PASSENGER);
    }

    private void bindAutoCheckbox(CheckBox checkBox, Seat seat) {
        checkBox.setOnCheckedChangeListener(null);
        checkBox.setChecked(service.isAutoEnabled(seat));
        checkBox.setOnCheckedChangeListener((button, checked) -> {
            if (service != null) {
                service.setAutoEnabled(seat, checked);
            }
        });
    }

    private void buildLevelButtons(LinearLayout row, boolean isDriver) {
        for (final int level : LEVELS) {
            Button button = new Button(this);
            button.setText(level == 0 ? "OFF" : String.valueOf(level));
            button.setTextSize(22);
            button.setGravity(Gravity.CENTER);
            button.setOnClickListener(v -> {
                if (service != null) {
                    service.setSeatHeat(isDriver, level);
                }
            });

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            row.addView(button, params);
        }
    }

    private void copyLog() {
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("AutoHeatProbe", logText()));
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
        runOnUiThread(() -> {
            statusView.setText(ready ? "HVAC подключён" : "HVAC недоступен");
            statusView.setTextColor(ready ? 0xFF8BC34A : 0xFFF44336);
        });
    }

    @Override
    public void onCabinTemperature(double celsius, int raw) {
        runOnUiThread(() ->
                temperatureView.setText(String.format(Locale.US, "%.1f °C", celsius)));
    }
}
