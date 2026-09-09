package com.wt.airconditioner;

import android.app.Activity;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

/**
 * Вкладка пресетов: сверху выбор сиденья, слева редактор со слайдерами
 * длительностей и порога, справа список сохранённых расписаний.
 *
 * Длительности задаются слайдерами, а не полями ввода: цифры пальцем на
 * сенсорном экране головы не набирают.
 */
final class PresetsPanel implements TabController {

    /**
     * Всё, что переживает экран, решает сервис: панель редактирует записи, но не
     * знает, какая из них сейчас работает на сиденье.
     */
    interface Listener {
        void onApply(Preset preset);

        /** Пресет, который ведёт это сиденье, или null: знает только сервис. */
        String runningPreset(Seat seat);

        /** Снять пресет с сиденья и отдать его ручному управлению. */
        void onStop(Preset preset);

        /**
         * Запись заменена или удалена (newEncoded == null). Сервис хранит
         * «последний пресет сиденья» строкой самого пресета: без уведомления
         * указатель остался бы на исчезнувшем расписании.
         */
        void onPresetChanged(String oldEncoded, String newEncoded);
    }

    /** Значения слайдера порога — те же, что в TemperatureConstants. */
    private static final int[] THRESHOLDS = {-5, 0, 5, 10, 15};
    private static final int DEFAULT_THRESHOLD_INDEX = 2;
    private static final int MAX_MINUTES = 15;

    /**
     * Стартовые длительности нового пресета — не расписание из
     * TemperatureConstants: у пресета своя логика. Порядок как в массиве
     * minutes, от первого уровня.
     */
    private static final int[] DEFAULT_MINUTES = {2, 5, 10};

    private static final int CARD_RADIUS_DP = 12;
    private static final int BADGE_RADIUS_DP = 12;
    private static final int PILL_RADIUS_DP = 16;
    /** Подложка карточки пресета — темнее фона списка, но не глухая. */
    private static final int CARD_FILL = 0x66000000;

    private final Activity activity;
    private final PresetStore store;
    private final Listener listener;
    private ThemePalette palette;

    private View page;
    private LinearLayout list;
    private LinearLayout levelsContainer;
    private SeekBar thresholdBar;
    private TextView thresholdTitle;
    private LinearLayout thresholdLabels;
    private LinearLayout seatSegments;

    private Seat selectedSeat = Seat.DRIVER;

    /** Минуты по индексу уровня: [0] — уровень 1, [2] — уровень 3. */
    private final int[] minutes = DEFAULT_MINUTES.clone();

    /**
     * Пресет, открытый по карандашу, в том виде, в каком он лежит в хранилище.
     * null — редактор заполняют с нуля, и сохранение добавит новую запись.
     */
    private String editing;

    PresetsPanel(Activity activity, PresetStore store, ThemePalette palette, Listener listener) {
        this.activity = activity;
        this.store = store;
        this.palette = palette;
        this.listener = listener;
    }

    /**
     * Только разовая привязка: всё, что зависит от палитры, рисует applyTheme —
     * его зовут сразу следом. Кнопки не красим, их находит Ui.paintButtons.
     */
    @Override
    public void bind(View page) {
        this.page = page;
        list = page.findViewById(R.id.presetList);
        levelsContainer = page.findViewById(R.id.presetLevels);
        thresholdBar = page.findViewById(R.id.presetThreshold);
        thresholdTitle = page.findViewById(R.id.presetThresholdTitle);
        thresholdLabels = page.findViewById(R.id.presetThresholdLabels);
        seatSegments = page.findViewById(R.id.presetSeatSegments);

        bindThreshold();
        page.findViewById(R.id.presetSave).setOnClickListener(v -> askNameAndSave());
        page.findViewById(R.id.presetNew).setOnClickListener(v -> resetEditor());
    }

    /**
     * Выбранный порог переживает смену темы сам: слайдер перекрашивается, а не
     * пересобирается, поэтому его прогресс сохранять не нужно.
     */
    @Override
    public void applyTheme(ThemePalette palette) {
        this.palette = palette;
        buildSeatSegments();
        buildLevelSliders();
        paintSeekBar(thresholdBar, THRESHOLDS.length - 1);
        buildThresholdLabels();
        page.findViewById(R.id.presetDivider).setBackgroundColor(palette.divider);
        render();
    }

    /** Кнопка play/pause зависит от того, что греет, а меняют это на соседней вкладке. */
    @Override
    public void onTabVisible(boolean visible) {
        if (visible) {
            render();
        }
    }

    private void buildSeatSegments() {
        SegmentedControl.render(seatSegments,
                new SegmentedControl.Item[]{
                        new SegmentedControl.Item(Seat.DRIVER.label),
                        new SegmentedControl.Item(Seat.PASSENGER.label)},
                selectedSeat.ordinal(), palette, 18, 50, 24,
                index -> selectSeat(Seat.values()[index]));
    }

    /** Три строки «номер уровня — слайдер — длительность». */
    private void buildLevelSliders() {
        levelsContainer.removeAllViews();
        for (int index = 0; index < minutes.length; index++) {
            levelsContainer.addView(buildLevelRow(index));
        }
    }

    private View buildLevelRow(int index) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(activity, 8), 0, Ui.dp(activity, 8));

        row.addView(levelBadge(index + 1));

        SeekBar bar = new SeekBar(activity);
        bar.setMax(MAX_MINUTES);
        bar.setProgress(minutes[index]);
        paintSeekBar(bar, MAX_MINUTES);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        barParams.setMarginStart(Ui.dp(activity, 12));
        barParams.setMarginEnd(Ui.dp(activity, 12));
        row.addView(bar, barParams);

        TextView duration = durationPill(minutes[index]);
        row.addView(duration);

        final int level = index;
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                minutes[level] = progress;
                duration.setText(minutesText(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        return row;
    }

    private void bindThreshold() {
        thresholdBar.setMax(THRESHOLDS.length - 1);
        thresholdBar.setProgress(DEFAULT_THRESHOLD_INDEX);
        thresholdBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateThresholdTitle();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        updateThresholdTitle();
    }

    private void updateThresholdTitle() {
        thresholdTitle.setText("Включать, когда температура в салоне ниже "
                + THRESHOLDS[thresholdBar.getProgress()] + "°C");
    }

    /** Подписи под слайдером порога: -5, 0, 5, 10, 15 °C. */
    private void buildThresholdLabels() {
        LinearLayout labels = thresholdLabels;
        labels.removeAllViews();
        for (int index = 0; index < THRESHOLDS.length; index++) {
            TextView label = new TextView(activity);
            label.setText(THRESHOLDS[index] + "°C");
            label.setTextSize(13);
            label.setTextColor(Ui.color(activity, R.color.text_body));
            label.setTypeface(Fonts.regular(activity));
            label.setGravity(index == 0 ? Gravity.START
                    : index == THRESHOLDS.length - 1 ? Gravity.END : Gravity.CENTER);
            labels.addView(label, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
    }

    /** Имя спрашиваем при сохранении, а не в редакторе. */
    private void askNameAndSave() {
        AppDialog.prompt(activity, palette, "Сохранение пресета", "Название пресета",
                "Сохранить", this::save);
    }

    private void save(String name) {
        Preset preset = Preset.fromInput(name, selectedSeat,
                minutes[2], minutes[1], minutes[0],
                THRESHOLDS[thresholdBar.getProgress()]);

        if (preset == null) {
            Toast.makeText(activity,
                    "Нужны название и хотя бы одна ненулевая длительность",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (editing != null && store.replace(editing, preset)) {
            listener.onPresetChanged(editing, preset.encode());
        } else if (!store.add(preset)) {
            Toast.makeText(activity, "Такой пресет уже сохранён", Toast.LENGTH_LONG).show();
            return;
        }
        editing = null;
        render();
    }

    private void resetEditor() {
        editing = null;
        System.arraycopy(DEFAULT_MINUTES, 0, minutes, 0, minutes.length);
        thresholdBar.setProgress(DEFAULT_THRESHOLD_INDEX);
        buildLevelSliders();
    }

    /**
     * Единственная смена сиденья — и по сегменту сверху, и по кнопке «Пресеты» с
     * главной вкладки.
     *
     * Черновик принадлежит сиденью и уезжает вместе с ним: сохранение после
     * переключения записало бы правку в пресет соседнего сиденья, заодно
     * подменив ему активное расписание.
     */
    void selectSeat(Seat seat) {
        if (selectedSeat == seat) {
            return;
        }
        selectedSeat = seat;
        resetEditor();
        buildSeatSegments();
        render();
    }

    /** Список показывает пресеты выбранного сиденья. */
    void render() {
        list.removeAllViews();
        List<Preset> presets = store.load();
        String running = listener.runningPreset(selectedSeat);

        boolean empty = true;
        for (int index = 0; index < presets.size(); index++) {
            Preset preset = presets.get(index);
            if (preset.seat != selectedSeat) {
                continue;
            }
            empty = false;
            list.addView(buildCard(preset, index, preset.encode().equals(running)));
        }

        if (empty) {
            TextView placeholder = new TextView(activity);
            placeholder.setText("Пока нет сохранённых пресетов");
            placeholder.setTextColor(Ui.color(activity, R.color.text_secondary));
            placeholder.setTextSize(16);
            placeholder.setTypeface(Fonts.regular(activity));
            placeholder.setGravity(Gravity.CENTER);
            placeholder.setPadding(0, Ui.dp(activity, 40), 0, 0);
            list.addView(placeholder);
        }
    }

    private View buildCard(Preset preset, int index, boolean running) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(Ui.dp(activity, 16), Ui.dp(activity, 10), Ui.dp(activity, 10), Ui.dp(activity, 10));

        card.setBackground(Ui.roundRect(activity, CARD_RADIUS_DP, CARD_FILL, palette.chipStroke));

        TextView title = new TextView(activity);
        title.setText(preset.name);
        title.setTextColor(Ui.color(activity, R.color.text_body));
        title.setTextSize(18);
        title.setTypeface(Fonts.regular(activity));
        card.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView schedule = new TextView(activity);
        schedule.setText(String.format(Locale.US, "%d \u2014 %d \u2014 %d мин, до %.0f °C",
                preset.settings.sequence.level3Minutes,
                preset.settings.sequence.level2Minutes,
                preset.settings.sequence.level1Minutes,
                preset.settings.thresholdCelsius));
        schedule.setTextColor(Ui.color(activity, R.color.text_secondary));
        schedule.setTextSize(17);
        schedule.setTypeface(Fonts.regular(activity));
        LinearLayout.LayoutParams scheduleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        /**
         * Отступ до кнопок действий: иначе расписание липнет к карандашу.
         */
        scheduleParams.setMarginEnd(Ui.dp(activity, 28));
        card.addView(schedule, scheduleParams);

        card.addView(iconButton(R.drawable.ic_edit, palette.accent, v -> loadIntoEditor(preset)));

        /**
         * Пресет запущен — та же кнопка его снимает.
         */
        card.addView(iconButton(running ? R.drawable.ic_pause : R.drawable.ic_play,
                palette.accent,
                v -> {
                    if (running) {
                        listener.onStop(preset);
                    } else {
                        listener.onApply(preset);
                    }
                }));
        card.addView(iconButton(R.drawable.ic_delete, 0xFFE53935, v ->
                AppDialog.confirm(activity, palette, "Удаление пресета",
                        "Удалить пресет «" + preset.name + "»?", "Удалить", () -> {
                            store.removeAt(index);
                            listener.onPresetChanged(preset.encode(), null);
                            if (preset.encode().equals(editing)) {
                                editing = null;
                            }
                            render();
                        })));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = Ui.dp(activity, 12);
        card.setLayoutParams(params);
        return card;
    }

    /** Правка пресета: значения уезжают в редактор. */
    private void loadIntoEditor(Preset preset) {
        editing = preset.encode();
        minutes[0] = preset.settings.sequence.level1Minutes;
        minutes[1] = preset.settings.sequence.level2Minutes;
        minutes[2] = preset.settings.sequence.level3Minutes;
        for (int index = 0; index < THRESHOLDS.length; index++) {
            if (THRESHOLDS[index] == Math.round(preset.settings.thresholdCelsius)) {
                thresholdBar.setProgress(index);
            }
        }
        buildLevelSliders();
    }

    private ImageView iconButton(int iconRes, int color, View.OnClickListener listener) {
        ImageView button = new ImageView(activity);
        button.setImageResource(iconRes);
        button.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        button.setPadding(Ui.dp(activity, 10), Ui.dp(activity, 10), Ui.dp(activity, 10), Ui.dp(activity, 10));
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(Ui.dp(activity, 44), Ui.dp(activity, 44));
        params.setMarginStart(Ui.dp(activity, 4));
        button.setLayoutParams(params);
        return button;
    }

    /** Бейдж с номером уровня слева от слайдера. */
    private TextView levelBadge(int level) {
        TextView badge = new TextView(activity);
        badge.setText(String.valueOf(level));
        badge.setTextSize(10);
        badge.setTextColor(palette.accent);
        badge.setTypeface(Fonts.bold(activity));
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(Ui.dp(activity, 8), Ui.dp(activity, 4), Ui.dp(activity, 8), Ui.dp(activity, 4));

        badge.setBackground(Ui.roundRect(activity, BADGE_RADIUS_DP, palette.chipFill,
                palette.accent));
        return badge;
    }

    private TextView durationPill(int value) {
        TextView pill = new TextView(activity);
        pill.setText(minutesText(value));
        pill.setTextSize(13);
        pill.setTextColor(palette.accent);
        pill.setTypeface(Fonts.regular(activity));
        pill.setGravity(Gravity.CENTER);

        pill.setBackground(Ui.roundRect(activity, PILL_RADIUS_DP, palette.chipFill,
                palette.chipStroke));
        pill.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(activity, 90), Ui.dp(activity, 32)));
        return pill;
    }

    private String minutesText(int value) {
        return value + " мин.";
    }

    /**
     * Толстый трек с делениями и круглый ползунок: системный SeekBar рисует
     * тонкую линию, поэтому и трек, и ползунок задаются свои.
     */
    private void paintSeekBar(SeekBar bar, int divisions) {
        /**
         * Слой обязан иметь id «progress»: иначе SeekBar сбросит уровень трека
         * в ноль, обновляя вторичный прогресс, и заливка не появится.
         */
        LayerDrawable track = new LayerDrawable(
                new Drawable[]{new SliderTrack(palette.accent, divisions, Ui.dp(activity, 8), Ui.dp(activity, 2))});
        track.setId(0, android.R.id.progress);
        bar.setProgressDrawable(track);

        GradientDrawable thumb = Ui.oval(palette.accent);
        thumb.setSize(Ui.dp(activity, 20), Ui.dp(activity, 20));
        bar.setThumb(thumb);
        bar.setThumbOffset(0);
        bar.setPadding(Ui.dp(activity, 10), Ui.dp(activity, 8), Ui.dp(activity, 10), Ui.dp(activity, 8));
        bar.setSplitTrack(false);
    }

}
