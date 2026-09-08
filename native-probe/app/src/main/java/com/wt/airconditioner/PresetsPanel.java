package com.wt.airconditioner;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * Вкладка пресетов: список сохранённых расписаний слева, редактор справа.
 *
 * Вынесено из MainActivity, чтобы та осталась про одно — состояние сервиса и
 * ручное управление уровнями.
 */
final class PresetsPanel {

    /** Что делать с выбранным пресетом; знает только сервис. */
    interface OnApply {
        void apply(Preset preset);
    }

    private final Activity activity;
    private final PresetStore store;
    private final OnApply onApply;
    private final int accent;

    private final LinearLayout list;
    private final EditText name;
    private Seat selectedSeat = Seat.DRIVER;
    private final EditText level3;
    private final EditText level2;
    private final EditText level1;
    private final EditText threshold;

    PresetsPanel(Activity activity, PresetStore store, int accent, OnApply onApply) {
        this.activity = activity;
        this.store = store;
        this.accent = accent;
        this.onApply = onApply;

        list = activity.findViewById(R.id.presetList);
        name = activity.findViewById(R.id.presetName);
        buildSeatSegments();
        level3 = activity.findViewById(R.id.presetLevel3);
        level2 = activity.findViewById(R.id.presetLevel2);
        level1 = activity.findViewById(R.id.presetLevel1);
        threshold = activity.findViewById(R.id.presetThreshold);

        activity.findViewById(R.id.presetSave).setOnClickListener(v -> savePreset());

        render();
    }

    private void buildSeatSegments() {
        SegmentedControl.build(activity.findViewById(R.id.presetSeatSegments),
                new String[]{Seat.DRIVER.label, Seat.PASSENGER.label},
                selectedSeat.ordinal(), accent, 17, index -> {
                    selectedSeat = Seat.values()[index];
                    buildSeatSegments();
                });
    }

    private void savePreset() {
        Preset preset = Preset.fromInput(name.getText().toString(), selectedSeat,
                level3.getText().toString(), level2.getText().toString(),
                level1.getText().toString(), threshold.getText().toString());

        if (preset == null) {
            Toast.makeText(activity,
                    "Нужны название и хотя бы одна ненулевая длительность",
                    Toast.LENGTH_LONG).show();
            return;
        }
        store.add(preset);
        clearForm();
        render();
    }

    private void clearForm() {
        name.setText("");
        level3.setText("");
        level2.setText("");
        level1.setText("");
        threshold.setText("");
    }

    /** Перерисовка списка целиком: пресетов единицы, экономить тут не на чем. */
    private void render() {
        list.removeAllViews();
        List<Preset> presets = store.load();

        if (presets.isEmpty()) {
            TextView empty = new TextView(activity);
            empty.setText("Пресетов пока нет");
            empty.setTextColor(0xFF78909C);
            empty.setTypeface(Fonts.regular(activity));
            list.addView(empty);
            return;
        }

        for (int index = 0; index < presets.size(); index++) {
            list.addView(buildRow(presets.get(index), index));
        }
    }

    private View buildRow(Preset preset, int index) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));

        TextView title = new TextView(activity);
        title.setText(preset.describe());
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(16);
        title.setTypeface(Fonts.regular(activity));
        row.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        row.addView(pillButton("Применить", v -> onApply.apply(preset), true));
        row.addView(pillButton("Удалить", v -> {
            store.removeAt(index);
            render();
        }, false));

        return row;
    }

    /** Кнопки того же вида, что и везде: залитая акцентом или обведённая им. */
    private TextView pillButton(String text, View.OnClickListener listener, boolean filled) {
        TextView button = new TextView(activity);
        button.setText(text);
        button.setTextColor(filled ? Palette.textOn(accent) : 0xFFFFFFFF);
        button.setTextSize(15);
        button.setTypeface(Fonts.regular(activity));
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(18), dp(8), dp(18), dp(8));

        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(dp(30));
        shape.setColor(filled ? accent : Color.TRANSPARENT);
        shape.setStroke(dp(1), accent);
        button.setBackground(shape);
        button.setOnClickListener(listener);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMarginStart(dp(8));
        button.setLayoutParams(params);
        return button;
    }

    private int dp(float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
