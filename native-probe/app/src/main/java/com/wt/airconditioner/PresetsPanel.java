package com.wt.airconditioner;

import android.app.Activity;
import android.graphics.Color;
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
 * Вкладка пресетов — порт PresetsTab из Flutter-версии: сверху выбор сиденья,
 * слева редактор со слайдерами длительностей и порога, справа список
 * сохранённых расписаний.
 *
 * Длительности задаются слайдерами 0…15 минут, как в оригинале: цифры пальцем
 * на сенсорном экране головы не набирают.
 */
final class PresetsPanel {

    /** Что делать с выбранным пресетом; знает только сервис. */
    interface OnApply {
        void apply(Preset preset);
    }

    /** Значения слайдера порога — те же, что в TemperatureConstants оригинала. */
    private static final int[] THRESHOLDS = {-5, 0, 5, 10, 15};
    private static final int DEFAULT_THRESHOLD_INDEX = 2;
    private static final int MAX_MINUTES = 15;

    /** Стартовые длительности: расписание диапазона «cold» из оригинала. */
    private static final int[] DEFAULT_MINUTES = {8, 5, 10};

    private final Activity activity;
    private final PresetStore store;
    private final OnApply onApply;
    private final int accent;

    private final LinearLayout list;
    private final LinearLayout levelsContainer;
    private final SeekBar thresholdBar;

    private Seat selectedSeat = Seat.DRIVER;

    /** Минуты по индексу уровня: [0] — уровень 1, [2] — уровень 3. */
    private final int[] minutes = DEFAULT_MINUTES.clone();

    PresetsPanel(Activity activity, PresetStore store, int accent, OnApply onApply) {
        this.activity = activity;
        this.store = store;
        this.accent = accent;
        this.onApply = onApply;

        list = activity.findViewById(R.id.presetList);
        levelsContainer = activity.findViewById(R.id.presetLevels);
        thresholdBar = activity.findViewById(R.id.presetThreshold);

        buildSeatSegments();
        buildLevelSliders();
        buildThreshold();
        buildThresholdLabels();

        activity.findViewById(R.id.presetDivider).setBackgroundColor(withAlpha(accent, 70));
        paintButton(activity.findViewById(R.id.presetSave));
        paintButton(activity.findViewById(R.id.presetNew));
        activity.findViewById(R.id.presetSave).setOnClickListener(v -> askNameAndSave());
        activity.findViewById(R.id.presetNew).setOnClickListener(v -> resetEditor());

        render();
    }

    private void buildSeatSegments() {
        SegmentedControl.build(activity.findViewById(R.id.presetSeatSegments),
                new SegmentedControl.Item[]{
                        new SegmentedControl.Item(Seat.DRIVER.label),
                        new SegmentedControl.Item(Seat.PASSENGER.label)},
                selectedSeat.ordinal(), accent, 18, 50, 24, index -> {
                    selectedSeat = Seat.values()[index];
                    buildSeatSegments();
                    render();
                });
    }

    /** Три строки «номер уровня — слайдер — длительность», как в оригинале. */
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
        row.setPadding(0, dp(8), 0, dp(8));

        row.addView(levelBadge(index + 1));

        SeekBar bar = new SeekBar(activity);
        bar.setMax(MAX_MINUTES);
        bar.setProgress(minutes[index]);
        paintSeekBar(bar, MAX_MINUTES);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        barParams.setMarginStart(dp(12));
        barParams.setMarginEnd(dp(12));
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

    private void buildThreshold() {
        thresholdBar.setMax(THRESHOLDS.length - 1);
        thresholdBar.setProgress(DEFAULT_THRESHOLD_INDEX);
        paintSeekBar(thresholdBar, THRESHOLDS.length - 1);
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
        TextView title = activity.findViewById(R.id.presetThresholdTitle);
        title.setText("Включать, когда температура в салоне ниже "
                + THRESHOLDS[thresholdBar.getProgress()] + "°C");
    }

    /** Подписи под слайдером порога: -5, 0, 5, 10, 15 °C. */
    private void buildThresholdLabels() {
        LinearLayout labels = activity.findViewById(R.id.presetThresholdLabels);
        labels.removeAllViews();
        for (int index = 0; index < THRESHOLDS.length; index++) {
            TextView label = new TextView(activity);
            label.setText(THRESHOLDS[index] + "°C");
            label.setTextSize(13);
            label.setTextColor(0xFFFFFFFF);
            label.setTypeface(Fonts.regular(activity));
            label.setGravity(index == 0 ? Gravity.START
                    : index == THRESHOLDS.length - 1 ? Gravity.END : Gravity.CENTER);
            labels.addView(label, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
    }

    /** Имя спрашиваем при сохранении, как в SavePresetDialog оригинала. */
    private void askNameAndSave() {
        AppDialog.prompt(activity, accent, "Сохранение пресета", "Название пресета",
                "Сохранить", this::save);
    }

    private void save(String name) {
        Preset preset = Preset.fromInput(name, selectedSeat,
                String.valueOf(minutes[2]), String.valueOf(minutes[1]),
                String.valueOf(minutes[0]),
                String.valueOf(THRESHOLDS[thresholdBar.getProgress()]));

        if (preset == null) {
            Toast.makeText(activity,
                    "Нужны название и хотя бы одна ненулевая длительность",
                    Toast.LENGTH_LONG).show();
            return;
        }
        store.add(preset);
        render();
    }

    private void resetEditor() {
        System.arraycopy(DEFAULT_MINUTES, 0, minutes, 0, minutes.length);
        thresholdBar.setProgress(DEFAULT_THRESHOLD_INDEX);
        buildLevelSliders();
    }

    /**
     * Список показывает пресеты выбранного сиденья: в оригинале переключатель
     * сверху фильтрует именно его.
     */
    private void render() {
        list.removeAllViews();
        List<Preset> presets = store.load();

        boolean empty = true;
        for (int index = 0; index < presets.size(); index++) {
            Preset preset = presets.get(index);
            if (preset.seat != selectedSeat) {
                continue;
            }
            empty = false;
            list.addView(buildCard(preset, index));
        }

        if (empty) {
            TextView placeholder = new TextView(activity);
            placeholder.setText("Пока нет сохранённых пресетов");
            placeholder.setTextColor(0xFFB0BEC5);
            placeholder.setTextSize(16);
            placeholder.setTypeface(Fonts.regular(activity));
            placeholder.setGravity(Gravity.CENTER);
            placeholder.setPadding(0, dp(40), 0, 0);
            list.addView(placeholder);
        }
    }

    private View buildCard(Preset preset, int index) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(10), dp(10), dp(10));

        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(dp(12));
        shape.setColor(0x66000000);
        shape.setStroke(dp(1), withAlpha(accent, 120));
        card.setBackground(shape);

        TextView title = new TextView(activity);
        title.setText(preset.name);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(18);
        title.setTypeface(Fonts.regular(activity));
        card.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView schedule = new TextView(activity);
        schedule.setText(String.format(Locale.US, "%d/%d/%d мин, до %.0f °C",
                preset.settings.sequence.level3Minutes,
                preset.settings.sequence.level2Minutes,
                preset.settings.sequence.level1Minutes,
                preset.settings.thresholdCelsius));
        schedule.setTextColor(0xFFB0BEC5);
        schedule.setTextSize(14);
        schedule.setTypeface(Fonts.regular(activity));
        card.addView(schedule);

        card.addView(iconButton(R.drawable.ic_edit, accent, v -> loadIntoEditor(preset)));
        card.addView(iconButton(R.drawable.ic_play, accent, v -> onApply.apply(preset)));
        card.addView(iconButton(R.drawable.ic_delete, 0xFFE53935, v ->
                AppDialog.confirm(activity, accent, "Удаление пресета",
                        "Удалить пресет «" + preset.name + "»?", "Удалить", () -> {
                            store.removeAt(index);
                            render();
                        })));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(12);
        card.setLayoutParams(params);
        return card;
    }

    /** Правка пресета: значения уезжают в редактор, как по карандашу в оригинале. */
    private void loadIntoEditor(Preset preset) {
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
        button.setPadding(dp(10), dp(10), dp(10), dp(10));
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(44), dp(44));
        params.setMarginStart(dp(4));
        button.setLayoutParams(params);
        return button;
    }

    /** Бейдж с номером уровня слева от слайдера. */
    private TextView levelBadge(int level) {
        TextView badge = new TextView(activity);
        badge.setText(String.valueOf(level));
        badge.setTextSize(10);
        badge.setTextColor(accent);
        badge.setTypeface(Fonts.bold(activity));
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(8), dp(4), dp(8), dp(4));

        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(dp(12));
        shape.setColor(withAlpha(accent, 51));
        shape.setStroke(dp(1), accent);
        badge.setBackground(shape);
        return badge;
    }

    private TextView durationPill(int value) {
        TextView pill = new TextView(activity);
        pill.setText(minutesText(value));
        pill.setTextSize(13);
        pill.setTextColor(accent);
        pill.setTypeface(Fonts.regular(activity));
        pill.setGravity(Gravity.CENTER);

        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(dp(16));
        shape.setColor(withAlpha(accent, 51));
        shape.setStroke(dp(1), withAlpha(accent, 120));
        pill.setBackground(shape);
        pill.setLayoutParams(new LinearLayout.LayoutParams(dp(90), dp(32)));
        return pill;
    }

    private String minutesText(int value) {
        return value + " мин.";
    }

    /**
     * Слайдер как в оригинале: толстый трек с делениями и круглый ползунок
     * радиусом 10. Системный SeekBar рисует тонкую линию, поэтому и трек, и
     * ползунок задаются свои.
     */
    private void paintSeekBar(SeekBar bar, int divisions) {
        // Слой обязан иметь id «progress»: иначе SeekBar сбросит уровень трека
        // в ноль, обновляя вторичный прогресс, и заливка не появится.
        LayerDrawable track = new LayerDrawable(
                new Drawable[]{new SliderTrack(accent, divisions, dp(8), dp(2))});
        track.setId(0, android.R.id.progress);
        bar.setProgressDrawable(track);

        GradientDrawable thumb = new GradientDrawable();
        thumb.setShape(GradientDrawable.OVAL);
        thumb.setColor(accent);
        thumb.setSize(dp(20), dp(20));
        bar.setThumb(thumb);
        bar.setThumbOffset(0);
        bar.setPadding(dp(10), dp(8), dp(10), dp(8));
        bar.setSplitTrack(false);
    }

    private void paintButton(TextView button) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(dp(30));
        shape.setColor(accent);
        button.setBackground(shape);
        button.setTextColor(Palette.textOn(accent));
        button.setTypeface(Fonts.regular(activity));
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private int dp(float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
