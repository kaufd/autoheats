package com.wt.airconditioner;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Этап 1 миграции на native: проверяет ровно две вещи на реальной голове —
 * рисуется ли нативный UI (Flutter давал белый экран) и отвечает ли HVAC.
 *
 * Сознательно без AccessibilityService и foreground-service: если пробник не
 * запустится, причина должна быть однозначной. Их черёд — этап 2.
 */
public class MainActivity extends Activity implements CarHvacProbe.Listener {

    private static final int[] LEVELS = {0, 1, 2, 3};

    private TextView statusView;
    private TextView temperatureView;
    private TextView logView;
    private ScrollView logScroll;

    private final StringBuilder logBuffer = new StringBuilder();
    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    private CarHvacProbe probe;

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

        findViewById(R.id.readTemp).setOnClickListener(v -> probe.readCabinTemperature());
        findViewById(R.id.copyLog).setOnClickListener(v -> copyLog());

        onLog("UI создан — белого экрана нет");
        probe = new CarHvacProbe(this, this);
    }

    @Override
    protected void onDestroy() {
        if (probe != null) {
            probe.disconnect();
        }
        super.onDestroy();
    }

    private void buildLevelButtons(LinearLayout row, boolean isDriver) {
        for (final int level : LEVELS) {
            Button button = new Button(this);
            button.setText(level == 0 ? "OFF" : String.valueOf(level));
            button.setTextSize(22);
            button.setGravity(Gravity.CENTER);
            button.setOnClickListener(v -> probe.setSeatHeat(isDriver, level));

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

    // --- CarHvacProbe.Listener (всё приходит на main thread) ---

    @Override
    public void onLog(String message) {
        logBuffer.append(timeFormat.format(new Date())).append("  ").append(message).append('\n');
        logView.setText(logBuffer);
        logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
    }

    @Override
    public void onHvacReady(boolean ready) {
        statusView.setText(ready ? "HVAC подключён" : "HVAC недоступен");
        statusView.setTextColor(ready ? 0xFF8BC34A : 0xFFF44336);
    }

    @Override
    public void onCabinTemperature(double celsius, int raw) {
        temperatureView.setText(String.format(Locale.US, "%.1f °C", celsius));
        onLog("температура: raw=" + raw + " → " + String.format(Locale.US, "%.1f", celsius) + " °C");
    }
}
