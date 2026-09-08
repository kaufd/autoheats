package com.wt.airconditioner;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** Локальное представление диагностического лога и debug-инжектора. */
final class LogUiController {

    private static final int[] QUICK_TEMPERATURES = {-15, -10, -5, 0, 5, 10};
    private static final int LOG_TRIM_SLACK = 100;

    private final Activity activity;
    private final SeatHeatServiceProvider serviceProvider;
    private final Deque<String> logLines = new ArrayDeque<>();
    private final TextView logView;
    private final TextView counter;
    private final ScrollView logScroll;
    private int accent;

    LogUiController(Activity activity, SeatHeatServiceProvider serviceProvider, int accent) {
        this.activity = activity;
        this.serviceProvider = serviceProvider;
        this.accent = accent;
        logView = activity.findViewById(R.id.log);
        counter = activity.findViewById(R.id.logCounter);
        logScroll = activity.findViewById(R.id.logScroll);
    }

    void bind() {
        activity.findViewById(R.id.readTemp).setOnClickListener(v -> {
            SeatHeatService service = serviceProvider.get();
            if (service != null) {
                service.readCabinTemperature();
            }
        });
        activity.findViewById(R.id.copyLog).setOnClickListener(v -> copyLog());
        activity.findViewById(R.id.clearLog).setOnClickListener(v -> clearLog());
        activity.findViewById(R.id.injectTemp).setOnClickListener(v -> injectTemperature());
        activity.findViewById(R.id.restartCar).setOnClickListener(v -> {
            SeatHeatService service = serviceProvider.get();
            if (service != null) {
                service.restartCarConnection();
            }
        });
        buildQuickTemperatures();
    }

    void setAccent(int accent) {
        this.accent = accent;
        paintPanel(activity.findViewById(R.id.logScroll));
        paintPanel(activity.findViewById(R.id.injectPanel));
        buildQuickTemperatures();
    }

    void setInitialSnapshot(List<String> snapshot) {
        logLines.clear();
        if (snapshot != null) {
            logLines.addAll(snapshot);
        }
        renderLog();
    }

    void onTabShown() {
        scrollLogToBottom();
    }

    void clearLog() {
        SeatHeatService service = serviceProvider.get();
        if (service != null) {
            service.clearLogs();
        }
        logLines.clear();
        renderLog();
    }

    void onLogLine(String line) {
        activity.runOnUiThread(() -> {
            logLines.addLast(line);
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

    void log(String message) {
        SeatHeatService service = serviceProvider.get();
        if (service != null) {
            service.onLog(message);
        }
    }

    private void buildQuickTemperatures() {
        LinearLayout container = activity.findViewById(R.id.quickTemperatures);
        container.removeAllViews();

        LinearLayout row = null;
        for (int index = 0; index < QUICK_TEMPERATURES.length; index++) {
            if (index % 3 == 0) {
                row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                rowParams.topMargin = dp(6);
                container.addView(row, rowParams);
            }
            row.addView(quickTemperatureButton(QUICK_TEMPERATURES[index], index % 3 > 0));
        }
    }

    private TextView quickTemperatureButton(int celsius, boolean withMargin) {
        TextView button = new TextView(activity);
        button.setText(celsius + "°C");
        button.setTextSize(14);
        button.setTextColor(accent);
        button.setTypeface(Fonts.regular(activity));
        button.setGravity(android.view.Gravity.CENTER);

        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(dp(10));
        shape.setColor(withAlpha(accent, 51));
        shape.setStroke(dp(1), withAlpha(accent, 120));
        button.setBackground(shape);
        button.setOnClickListener(v -> {
            SeatHeatService service = serviceProvider.get();
            if (service != null) {
                service.injectTemperature(celsius);
            }
        });

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(38), 1f);
        if (withMargin) {
            params.setMarginStart(dp(6));
        }
        button.setLayoutParams(params);
        return button;
    }

    private void injectTemperature() {
        EditText field = activity.findViewById(R.id.injectValue);
        String text = field.getText().toString().trim().replace(',', '.');
        SeatHeatService service = serviceProvider.get();
        if (service == null || text.isEmpty()) {
            return;
        }
        try {
            service.injectTemperature(Double.parseDouble(text));
        } catch (NumberFormatException e) {
            Toast.makeText(activity, "Не похоже на температуру", Toast.LENGTH_SHORT).show();
        }
    }

    private void copyLog() {
        ClipboardManager clipboard =
                (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("AutoHeat", logText()));
        Toast.makeText(activity, "Лог скопирован", Toast.LENGTH_SHORT).show();
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
        counter.setText(logLines.size() + " / " + SeatHeatService.LOG_CAPACITY);
        scrollLogToBottom();
    }

    private void scrollLogToBottom() {
        CheckBox autoScroll = activity.findViewById(R.id.autoScroll);
        if (autoScroll != null && !autoScroll.isChecked()) {
            return;
        }
        logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void paintPanel(View panel) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(dp(12));
        shape.setColor(0x99000000);
        shape.setStroke(dp(1), withAlpha(accent, 90));
        panel.setBackground(shape);
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private int dp(float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
