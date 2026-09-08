package com.wt.airconditioner;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
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

    private final LinearLayout list;
    private final EditText name;
    private final RadioGroup seatGroup;
    private final EditText level3;
    private final EditText level2;
    private final EditText level1;
    private final EditText threshold;

    PresetsPanel(Activity activity, PresetStore store, OnApply onApply) {
        this.activity = activity;
        this.store = store;
        this.onApply = onApply;

        list = activity.findViewById(R.id.presetList);
        name = activity.findViewById(R.id.presetName);
        seatGroup = activity.findViewById(R.id.presetSeat);
        level3 = activity.findViewById(R.id.presetLevel3);
        level2 = activity.findViewById(R.id.presetLevel2);
        level1 = activity.findViewById(R.id.presetLevel1);
        threshold = activity.findViewById(R.id.presetThreshold);

        Button save = activity.findViewById(R.id.presetSave);
        save.setOnClickListener(v -> savePreset());

        render();
    }

    private void savePreset() {
        Seat seat = seatGroup.getCheckedRadioButtonId() == R.id.presetSeatPassenger
                ? Seat.PASSENGER
                : Seat.DRIVER;
        Preset preset = Preset.fromInput(name.getText().toString(), seat,
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

        TextView title = new TextView(activity);
        title.setText(preset.describe());
        title.setTextColor(0xFFECEFF1);
        title.setTextSize(14);
        row.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button apply = new Button(activity);
        apply.setText("Применить");
        apply.setOnClickListener(v -> onApply.apply(preset));
        row.addView(apply);

        Button delete = new Button(activity);
        delete.setText("Удалить");
        delete.setOnClickListener(v -> {
            store.removeAt(index);
            render();
        });
        row.addView(delete);

        return row;
    }
}
