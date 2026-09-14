package com.wt.airconditioner;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Локальное представление диагностического лога и debug-инжектора. */
final class LogUiController implements TabController {

    private static final int[] QUICK_TEMPERATURES = {-15, -10, -5, 0, 5, 10};
    private static final int LOG_TRIM_SLACK = 100;

    private static final int CHIP_RADIUS_DP = 10;
    private static final int PANEL_RADIUS_DP = 12;
    /** Подложка панелей: почти чёрная, чтобы лог читался поверх фона головы. */
    private static final int PANEL_FILL = 0x99000000;

    private final Activity activity;
    /**
     * Роль, а не сервис целиком: вкладке логов нечего делать с подогревом.
     * Supplier — потому что binding случается позже открытия экрана, и до него
     * здесь null.
     */
    private final Supplier<Diagnostics> diagnostics;
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

    LogUiController(Activity activity, Supplier<Diagnostics> diagnostics, ThemePalette palette) {
        this.activity = activity;
        this.diagnostics = diagnostics;
        this.palette = palette;
    }

    /**
     * Действие над сервисом, если он уже привязан. Шесть кнопок вкладки иначе
     * повторяли бы одну и ту же проверку на null.
     */
    private void withService(Consumer<Diagnostics> action) {
        Diagnostics service = diagnostics.get();
        if (service != null) {
            action.accept(service);
        }
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

        page.findViewById(R.id.startCascade).setOnClickListener(
                v -> withService(Diagnostics::startAutoHeatNow));
        page.findViewById(R.id.readTemp).setOnClickListener(
                v -> withService(Diagnostics::readCabinTemperature));
        page.findViewById(R.id.saveLog).setOnClickListener(v -> saveLog());
        page.findViewById(R.id.clearLog).setOnClickListener(v -> clearLog());
        page.findViewById(R.id.injectTemp).setOnClickListener(v -> injectTemperature());
        page.findViewById(R.id.restartCar).setOnClickListener(
                v -> withService(Diagnostics::restartCarConnection));
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
        withService(Diagnostics::clearLogs);
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
        withService(service -> service.log(message));
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
        button.setOnClickListener(v -> withService(service -> service.injectTemperature(celsius)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, Ui.dp(activity, 38), 1f);
        if (withMargin) {
            params.setMarginStart(Ui.dp(activity, 6));
        }
        button.setLayoutParams(params);
        return button;
    }

    private void injectTemperature() {
        String text = injectValue.getText().toString().trim().replace(',', '.');
        if (text.isEmpty()) {
            return;
        }
        try {
            double celsius = Double.parseDouble(text);
            withService(service -> service.injectTemperature(celsius));
        } catch (NumberFormatException e) {
            Toast.makeText(activity, "Не похоже на температуру", Toast.LENGTH_SHORT).show();
        }
    }

    /** Накопленные ряды калибровки во внутренней памяти приложения. */
    private List<File> calibrationFiles() {
        File directory = activity.getExternalFilesDir(null);
        File[] files = directory == null ? null : directory.listFiles(
                (dir, name) -> name.endsWith(CalibrationLog.EXTENSION));
        return files == null ? new ArrayList<>() : Arrays.asList(files);
    }

    private boolean copy(File from, File to) {
        try {
            Files.copy(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException | SecurityException e) {
            log("не скопировался " + from.getName() + ": " + e);
            return false;
        }
    }

    /**
     * Лог в файл, а не в буфер обмена: на голове его некуда вставить — ни
     * почты, ни редактора, ни adb.
     *
     * Пишем на съёмные носители и в Downloads — туда, откуда файл реально
     * забрать. Внутренняя память намеренно исключена: /sdcard/Android/data
     * штатными средствами головы не открывается, и файл там всё равно что
     * потерян. Downloads виден файловому менеджеру, но требует выданного
     * WRITE_EXTERNAL_STORAGE, которого приложение не запрашивает, поэтому
     * одной флешки может не оказаться достаточно — пишем в оба места.
     *
     * Пути уходят и в лог: тост гаснет, а прочитать, куда именно легло, нужно.
     */
    private void saveLog() {
        /** Штамп общий с рядом калибровки: в файловом менеджере они лягут рядом. */
        String name = "autoheat-log-" + CalibrationLog.stamp() + ".txt";
        String text = logText();
        List<String> saved = new ArrayList<>();

        List<File> targets = new ArrayList<>();
        File[] appDirs = activity.getExternalFilesDirs(null);
        /**
         * Нулевой каталог — primary external, то есть /sdcard/Android/data;
         * он пропускается. Все последующие появляются только при подключённом
         * съёмном носителе, и разрешений они не требуют.
         */
        for (int i = 1; i < appDirs.length; i++) {
            if (appDirs[i] != null) {
                targets.add(appDirs[i]);
            }
        }
        targets.add(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS));

        List<File> series = calibrationFiles();
        for (File dir : targets) {
            File file = new File(dir, name);
            try (Writer writer = new OutputStreamWriter(
                    new FileOutputStream(file), StandardCharsets.UTF_8)) {
                writer.write(text);
                saved.add(file.getAbsolutePath());
            } catch (IOException | SecurityException e) {
                /**
                 * Ожидаемо для Downloads без разрешения и для отсутствующей
                 * флешки: неудача одного места не отменяет остальные.
                 */
                log("не сохранилось в " + dir + ": " + e);
            }
            /**
             * Ряды калибровки копятся во внутренней памяти сами; наружу их
             * выносит только эта кнопка — вместе с логом, одним движением.
             */
            for (File csv : series) {
                File target = new File(dir, csv.getName());
                if (copy(csv, target)) {
                    saved.add(target.getAbsolutePath());
                }
            }
        }

        if (saved.isEmpty()) {
            Toast.makeText(activity, "Некуда сохранить — вставьте флешку (причина в логе)",
                    Toast.LENGTH_LONG).show();
            return;
        }
        for (String path : saved) {
            log("лог сохранён: " + path);
        }
        Toast.makeText(activity, "Сохранено (" + saved.size() + "): " + saved.get(0),
                Toast.LENGTH_LONG).show();
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
