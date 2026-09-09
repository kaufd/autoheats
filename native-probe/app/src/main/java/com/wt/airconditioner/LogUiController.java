package com.wt.airconditioner;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
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

    private static final int CHIP_RADIUS_DP = 10;
    private static final int PANEL_RADIUS_DP = 12;
    /** Подложка панелей: почти чёрная, чтобы лог читался поверх фона головы. */
    private static final int PANEL_FILL = 0x99000000;

    private final Activity activity;
    private final ServiceBindingController serviceProvider;
    private final Deque<String> logLines = new ArrayDeque<>();
    private final TextView logView;
    private final TextView counter;
    private final ScrollView logScroll;
    /** Полем, а не поиском по id: его читает каждая строка лога. */
    private final CheckBox autoScroll;
    private ThemePalette palette;

    LogUiController(Activity activity, ServiceBindingController serviceProvider, ThemePalette palette) {
        this.activity = activity;
        this.serviceProvider = serviceProvider;
        this.palette = palette;
        logView = activity.findViewById(R.id.log);
        counter = activity.findViewById(R.id.logCounter);
        logScroll = activity.findViewById(R.id.logScroll);
        autoScroll = activity.findViewById(R.id.autoScroll);
    }

    void bind() {
        activity.findViewById(R.id.startCascade).setOnClickListener(v -> {
            SeatHeatService service = serviceProvider.get();
            if (service != null) {
                service.startAutoHeatNow();
            }
        });
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
    }

    void applyTheme(ThemePalette palette) {
        this.palette = palette;
        paintPanel(activity.findViewById(R.id.logScroll));
        paintPanel(activity.findViewById(R.id.injectPanel));
        // Системный CheckBox рисуется дефолтным colorAccent платформы и при
        // смене темы оставался бирюзовым — видно только на запущенном
        // приложении, в разметке этого нет.
        autoScroll.setButtonTintList(ColorStateList.valueOf(palette.accent));
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

    /** Текущая температура рядом с инжектором — тем же текстом, что в шапке. */
    void onCabinTemperature(String text, int color) {
        TextView current = activity.findViewById(R.id.injectCurrent);
        current.setText(text);
        current.setTextColor(color);
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
            if (logLines.size() > LogBuffer.CAPACITY + LOG_TRIM_SLACK) {
                while (logLines.size() > LogBuffer.CAPACITY) {
                    logLines.removeFirst();
                }
                renderLog();
            } else {
                logView.append(line + "\n");
                // Счётчик обновляется и на быстром пути: renderLog случается
                // только при подрезке, и между подрезками цифра показывала бы
                // размер буфера получасовой давности.
                renderCounter();
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
                rowParams.topMargin = Ui.dp(activity, 6);
                container.addView(row, rowParams);
            }
            row.addView(quickTemperatureButton(QUICK_TEMPERATURES[index], index % 3 > 0));
        }
    }

    private TextView quickTemperatureButton(int celsius, boolean withMargin) {
        TextView button = new TextView(activity);
        button.setText(celsius + "°C");
        button.setTextSize(14);
        button.setTextColor(palette.accent);
        button.setTypeface(Fonts.regular(activity));
        button.setGravity(android.view.Gravity.CENTER);

        button.setBackground(Ui.roundRect(activity, CHIP_RADIUS_DP,
                palette.chipFill, palette.chipStroke));
        button.setOnClickListener(v -> {
            SeatHeatService service = serviceProvider.get();
            if (service != null) {
                service.injectTemperature(celsius);
            }
        });

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, Ui.dp(activity, 38), 1f);
        if (withMargin) {
            params.setMarginStart(Ui.dp(activity, 6));
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
        renderCounter();
        scrollLogToBottom();
    }

    private void renderCounter() {
        counter.setText(logLines.size() + " / " + LogBuffer.CAPACITY);
    }

    private void scrollLogToBottom() {
        if (autoScroll != null && !autoScroll.isChecked()) {
            return;
        }
        logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void paintPanel(View panel) {
        panel.setBackground(Ui.roundRect(activity, PANEL_RADIUS_DP, PANEL_FILL,
                palette.panelStroke));
    }

}
