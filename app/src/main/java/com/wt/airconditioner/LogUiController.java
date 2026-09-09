package com.wt.airconditioner;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** Локальное представление диагностического лога и debug-инжектора. */
final class LogUiController implements TabController {

    private static final int[] QUICK_TEMPERATURES = {-15, -10, -5, 0, 5, 10};
    private static final int LOG_TRIM_SLACK = 100;

    private static final int CHIP_RADIUS_DP = 10;
    private static final int PANEL_RADIUS_DP = 12;
    /** Подложка панелей: почти чёрная, чтобы лог читался поверх фона головы. */
    private static final int PANEL_FILL = 0x99000000;

    private final Activity activity;
    private final ServiceBindingController serviceProvider;
    private final Deque<String> logLines = new ArrayDeque<>();
    private ThemePalette palette;

    /** null, пока страницы нет: с выключенной отладкой адаптер её не создаёт. */
    private View page;
    private TextView logView;
    private TextView counter;
    private ScrollView logScroll;
    /** Полем, а не поиском по id: его читает каждая строка лога. */
    private CheckBox autoScroll;
    private TextView injectCurrent;
    private EditText injectValue;
    private LinearLayout quickTemperatures;

    /**
     * Страницы ViewPager разложены все сразу, поэтому невидимый лог — всё ещё
     * размеченный TextView, и append пересобирал бы его StaticLayout на каждую
     * строку. Пока вкладка скрыта, копится только текст; вид догоняет при показе.
     */
    private boolean visible;

    LogUiController(Activity activity, ServiceBindingController serviceProvider, ThemePalette palette) {
        this.activity = activity;
        this.serviceProvider = serviceProvider;
        this.palette = palette;
    }

    /** Страница создана адаптером: разбираем её и вешаем слушателей. */
    @Override
    public void bind(View page) {
        this.page = page;
        logView = page.findViewById(R.id.log);
        counter = page.findViewById(R.id.logCounter);
        logScroll = page.findViewById(R.id.logScroll);
        autoScroll = page.findViewById(R.id.autoScroll);
        injectCurrent = page.findViewById(R.id.injectCurrent);
        injectValue = page.findViewById(R.id.injectValue);
        quickTemperatures = page.findViewById(R.id.quickTemperatures);

        page.findViewById(R.id.startCascade).setOnClickListener(v -> {
            SeatHeatService service = serviceProvider.get();
            if (service != null) {
                service.startAutoHeatNow();
            }
        });
        page.findViewById(R.id.readTemp).setOnClickListener(v -> {
            SeatHeatService service = serviceProvider.get();
            if (service != null) {
                service.readCabinTemperature();
            }
        });
        page.findViewById(R.id.copyLog).setOnClickListener(v -> copyLog());
        page.findViewById(R.id.clearLog).setOnClickListener(v -> clearLog());
        page.findViewById(R.id.injectTemp).setOnClickListener(v -> injectTemperature());
        page.findViewById(R.id.restartCar).setOnClickListener(v -> {
            SeatHeatService service = serviceProvider.get();
            if (service != null) {
                service.restartCarConnection();
            }
        });
    }

    /** Отладку выключили — страницы больше нет, но лог копится дальше. */
    void unbind() {
        page = null;
        visible = false;
    }

    @Override
    public void applyTheme(ThemePalette palette) {
        this.palette = palette;
        if (page == null) {
            return;
        }
        paintPanel(logScroll);
        paintPanel(page.findViewById(R.id.injectPanel));
        ((ImageView) page.findViewById(R.id.injectThermometer))
                .setColorFilter(palette.accent, PorterDuff.Mode.SRC_IN);
        /**
         * Системный CheckBox рисуется дефолтным colorAccent платформы и при
         * смене темы оставался бирюзовым.
         */
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

    /**
     * На показе рисуем накопленное целиком и прокручиваем к последней строке —
     * этим же пользуются, повторно нажав кнопку «Логи».
     */
    @Override
    public void onTabVisible(boolean visible) {
        this.visible = visible && page != null;
        renderLog();
    }

    /** Текущая температура рядом с инжектором — тем же текстом, что и на сиденьях. */
    void onCabinTemperature(String text, int color) {
        if (page == null) {
            return;
        }
        injectCurrent.setText(text);
        injectCurrent.setTextColor(color);
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
            } else if (visible) {
                logView.append(line + "\n");
                /**
                 * Счётчик обновляется и на быстром пути: renderLog случается
                 * только при подрезке, а между ними цифра устаревала бы.
                 */
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
        LinearLayout container = quickTemperatures;
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
        button.setGravity(Gravity.CENTER);

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
        String text = injectValue.getText().toString().trim().replace(',', '.');
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
        if (!visible) {
            return;
        }
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
