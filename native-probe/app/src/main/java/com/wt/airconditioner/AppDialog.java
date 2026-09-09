package com.wt.airconditioner;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Диалоги в оформлении Flutter-версии (CustomAlertDialog): тёмная карточка со
 * скруглением 16, заголовок, содержимое и пара кнопок — «Отмена» обведена
 * акцентом, подтверждение залито им.
 *
 * Системный AlertDialog выглядит чужеродно на фоне остального экрана, поэтому
 * карточка собирается вручную.
 */
final class AppDialog {

    /** Colors.grey[900] из оригинала. */
    private static final int CARD = 0xFF212121;
    private static final int DISABLED = 0xFFACACAC;

    interface OnName {
        void onName(String name);
    }

    interface OnConfirm {
        void onConfirm();
    }

    private AppDialog() {
    }

    /**
     * Запрос имени. Кнопка подтверждения неактивна, пока поле пустое: пресет
     * без имени не найти в списке.
     */
    static void prompt(Activity activity, ThemePalette palette, String title, String hint,
            String confirmText, OnName listener) {
        Dialog dialog = card(activity);
        LinearLayout content = (LinearLayout) dialog.findViewById(android.R.id.content)
                .findViewWithTag("content");

        content.addView(title(activity, title));

        EditText input = new EditText(activity);
        input.setHint(hint);
        // Одна строка: перевод строки в имени пресета — единственный символ,
        // способный разорвать текстовое хранилище (Preset.RECORD). Preset его
        // всё равно вычищает, но не пускать его с клавиатуры дешевле, чем
        // чинить потом.
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setSingleLine(true);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(0xB3FFFFFF);
        input.setTextSize(18);
        input.setTypeface(Fonts.regular(activity));
        input.setPadding(Ui.dp(activity, 16), Ui.dp(activity, 14), Ui.dp(activity, 16), Ui.dp(activity, 14));

        GradientDrawable field = new GradientDrawable();
        field.setShape(GradientDrawable.RECTANGLE);
        field.setCornerRadius(Ui.dp(activity, 10));
        field.setColor(Color.TRANSPARENT);
        field.setStroke(Ui.dp(activity, 1), palette.accent);
        input.setBackground(field);

        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inputParams.topMargin = Ui.dp(activity, 20);
        content.addView(input, inputParams);

        TextView cancel = button(activity, "Отмена", palette, false, true);
        TextView confirm = button(activity, confirmText, palette, true, false);
        content.addView(buttons(activity, cancel, confirm));

        cancel.setOnClickListener(v -> dialog.dismiss());
        confirm.setOnClickListener(v -> {
            listener.onName(input.getText().toString());
            dialog.dismiss();
        });

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilled(activity, confirm, palette, s.toString().trim().length() > 0);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        dialog.show();
        input.requestFocus();
    }

    /** Подтверждение необратимого действия — например, удаления пресета. */
    static void confirm(Activity activity, ThemePalette palette, String title, String message,
            String confirmText, OnConfirm listener) {
        Dialog dialog = card(activity);
        LinearLayout content = (LinearLayout) dialog.findViewById(android.R.id.content)
                .findViewWithTag("content");

        content.addView(title(activity, title));

        TextView text = new TextView(activity);
        text.setText(message);
        text.setTextColor(Color.WHITE);
        text.setTextSize(16);
        text.setTypeface(Fonts.regular(activity));
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textParams.topMargin = Ui.dp(activity, 16);
        content.addView(text, textParams);

        TextView cancel = button(activity, "Отмена", palette, false, true);
        TextView ok = button(activity, confirmText, palette, true, true);
        content.addView(buttons(activity, cancel, ok));

        cancel.setOnClickListener(v -> dialog.dismiss());
        ok.setOnClickListener(v -> {
            listener.onConfirm();
            dialog.dismiss();
        });
        dialog.show();
    }

    private static Dialog card(Activity activity) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout content = new LinearLayout(activity);
        content.setTag("content");
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(activity, 24), Ui.dp(activity, 24), Ui.dp(activity, 24), Ui.dp(activity, 24));

        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(Ui.dp(activity, 16));
        shape.setColor(CARD);
        content.setBackground(shape);

        dialog.setContentView(content, new ViewGroup.LayoutParams(
                Ui.dp(activity, 520), ViewGroup.LayoutParams.WRAP_CONTENT));
        Window window = dialog.getWindow();
        if (window != null) {
            // Прозрачный фон окна: иначе под нашей карточкой видна системная.
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        return dialog;
    }

    private static TextView title(Activity activity, String text) {
        TextView title = new TextView(activity);
        title.setText(text);
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setTypeface(Fonts.bold(activity));
        return title;
    }

    private static LinearLayout buttons(Activity activity, TextView cancel, TextView confirm) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = Ui.dp(activity, 24);
        row.setLayoutParams(rowParams);

        LinearLayout.LayoutParams half =
                new LinearLayout.LayoutParams(0, Ui.dp(activity, 48), 1f);
        row.addView(cancel, half);

        LinearLayout.LayoutParams halfWithGap =
                new LinearLayout.LayoutParams(0, Ui.dp(activity, 48), 1f);
        halfWithGap.setMarginStart(Ui.dp(activity, 16));
        row.addView(confirm, halfWithGap);
        return row;
    }

    private static TextView button(Activity activity, String text, ThemePalette palette,
            boolean filled, boolean enabled) {
        TextView button = new TextView(activity);
        button.setText(text);
        button.setTextSize(16);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(Fonts.regular(activity));

        if (filled) {
            applyFilled(activity, button, palette, enabled);
            return button;
        }
        Ui.paintOutlineButton(button, palette);
        return button;
    }

    /** Выключенная кнопка подтверждения серая и не нажимается — как в оригинале. */
    private static void applyFilled(Activity activity, TextView button, ThemePalette palette,
            boolean enabled) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(Ui.dp(activity, 30));
        shape.setColor(enabled ? palette.accent : DISABLED);
        button.setBackground(shape);
        button.setTextColor(enabled ? palette.textOnAccent : 0xB3FFFFFF);
        button.setEnabled(enabled);
    }

}
