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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/**
 * Экран управления. Соединением с Car владеет SeatHeatService — Activity
 * только показывает его состояние и передаёт команды, поэтому закрытие экрана
 * не рвёт связь с автомобилем.
 */
public class MainActivity extends Activity implements SeatHeatService.UiListener {

    private static final int[] LEVELS = {0, 1, 2, 3};

    private TextView statusView;
    private TextView temperatureView;
    private TextView logView;
    private ScrollView logScroll;

    private final StringBuilder logBuffer = new StringBuilder();

    private SeatHeatService service;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((SeatHeatService.LocalBinder) binder).getService();
            // Лог сервиса мог начаться до открытия экрана — забираем целиком.
            logBuffer.setLength(0);
            for (String line : service.logSnapshot()) {
                logBuffer.append(line).append('\n');
            }
            logView.setText(logBuffer);
            scrollLogToBottom();
            service.setUiListener(MainActivity.this);
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
        logView = findViewById(R.id.log);
        logScroll = findViewById(R.id.logScroll);

        buildLevelButtons(findViewById(R.id.driverRow), true);
        buildLevelButtons(findViewById(R.id.passengerRow), false);

        findViewById(R.id.readTemp).setOnClickListener(v -> {
            if (service != null) {
                service.readCabinTemperature();
            }
        });
        findViewById(R.id.copyLog).setOnClickListener(v -> copyLog());

        Intent intent = new Intent(this, SeatHeatService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        bindService(intent, connection, 0);
    }

    @Override
    protected void onDestroy() {
        if (service != null) {
            service.setUiListener(null);
        }
        unbindService(connection);
        // Сервис намеренно не останавливаем: он должен пережить закрытие
        // экрана, иначе автовыключение по зажиганию перестанет работать.
        super.onDestroy();
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
        clipboard.setPrimaryClip(ClipData.newPlainText("AutoHeatProbe", logBuffer.toString()));
        Toast.makeText(this, "Лог скопирован", Toast.LENGTH_SHORT).show();
    }

    private void scrollLogToBottom() {
        logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
    }

    // --- SeatHeatService.UiListener (приходит с потока сервиса) ---

    @Override
    public void onLogLine(String line) {
        runOnUiThread(() -> {
            logBuffer.append(line).append('\n');
            logView.setText(logBuffer);
            scrollLogToBottom();
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
