// FILE: lib/src/presentation/ui/custom_alert_dialog.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Общий themed alert dialog с cancel/confirm actions.
//   SCOPE: dialog chrome, cancel button, optional primary confirm button.
//   DEPENDS: M-THEME, M-UI-SETTINGS, M-UI-PRESETS
//   LINKS: M-THEME, V-M-UI-SETTINGS, V-M-THEME
//   ROLE: RUNTIME
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   CustomAlertDialog - themed AlertDialog wrapper
//   build - renders content and contrast-safe action buttons
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.1.0 - White theme primary confirm button contrast]
// END_CHANGE_SUMMARY

import 'package:autoheat/src/extensions/context_extensions.dart';
import 'package:flutter/material.dart';

class CustomAlertDialog extends StatelessWidget {
  final String? title;
  final Widget content;
  final String? confirmText;
  final VoidCallback? onCancel;
  final VoidCallback? onConfirm;
  final bool isConfirmEnabled;

  const CustomAlertDialog({
    super.key,
    this.title,
    required this.content,
    this.confirmText,
    this.onCancel,
    this.onConfirm,
    this.isConfirmEnabled = true,
  });

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      backgroundColor: Colors.grey[900],
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
      ),
      contentPadding: const EdgeInsets.all(24),
      title: title != null
          ? Text(title!, style: context.textStyle.heading3)
          : null,
      content: content,
      actions: [
        Row(
          children: [
            Expanded(
              child: TextButton(
                onPressed: onCancel ?? () => Navigator.of(context).pop(),
                style: ButtonStyle(
                  backgroundColor: WidgetStatePropertyAll(Colors.transparent),
                  side: WidgetStatePropertyAll(
                    BorderSide(
                      color: context.themeColors.backgroundButtonPrimary,
                      width: 1,
                    ),
                  ),
                ),
                child: const Text('Отмена'),
              ),
            ),
            const SizedBox(width: 16),
            if (confirmText != null)
              Expanded(
                child: TextButton(
                  onPressed: isConfirmEnabled ? onConfirm : null,
                  style: ButtonStyle(
                    foregroundColor: WidgetStateProperty.resolveWith(
                      (states) => states.contains(WidgetState.disabled)
                          ? context.themeColors.textMuted
                          : context.themeColors.textButtonSelected,
                    ),
                    backgroundColor: WidgetStatePropertyAll(
                      isConfirmEnabled
                          ? context.themeColors.backgroundButtonPrimary
                          : context.themeColors.backgroundButtonInactive,
                    ),
                  ),
                  child: Text(confirmText!),
                ),
              ),
          ],
        ),
      ],
    );
  }
}
