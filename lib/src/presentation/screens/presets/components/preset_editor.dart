// FILE: lib/src/presentation/screens/presets/components/preset_editor.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Editor pane для одного пресета: name (new draft only), threshold + level sliders, Save button.
//   SCOPE: read-only props + onChange callbacks, no Bloc access, parent owns state.
//   DEPENDS: M-UI-PRESETS, M-MANUAL-SETTINGS, M-ENUMS, M-THEME
//   LINKS: M-UI-PRESETS, DF-PRESET-APPLY, FA-001
//   ROLE: RUNTIME
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   PresetEditor - StatelessWidget: sliders + optional name field + save button
//   _buildHeader - title or name TextField depending on isNewPresetDraft
//   _buildSaveButton - styled TextButton wired to onSave (disabled when invalid)
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.0.0 - Initial PresetEditor for merged Presets tab]
// END_CHANGE_SUMMARY

import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/extensions/context_extensions.dart';
import 'package:autoheat/src/models/manual_settings.dart';
import 'package:autoheat/src/presentation/screens/settings/components/manual_settings_section.dart';
import 'package:flutter/material.dart';

class PresetEditor extends StatelessWidget {
  final UserType userType;
  final ManualHeatSettings settings;
  final String? presetName; // null when editing default/empty state
  final bool isActive; // true when this preset is the user's selectedPreset
  final bool isNewPresetDraft;
  final TextEditingController? nameController;
  final bool isSaveEnabled;
  final void Function(AutoHeatLevel, int) onAutoHeatLevelChanged;
  final ValueChanged<double> onTemperatureThresholdChanged;
  final VoidCallback onSave;

  const PresetEditor({
    super.key,
    required this.userType,
    required this.settings,
    required this.presetName,
    required this.isActive,
    required this.isNewPresetDraft,
    required this.nameController,
    required this.isSaveEnabled,
    required this.onAutoHeatLevelChanged,
    required this.onTemperatureThresholdChanged,
    required this.onSave,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _buildHeader(context),
          const SizedBox(height: 8),
          ManualSettingsSection(
            userType: userType,
            settings: settings,
            onAutoHeatLevelChanged: onAutoHeatLevelChanged,
            onTemperatureThresholdChanged: onTemperatureThresholdChanged,
          ),
          const SizedBox(height: 16),
          _buildSaveButton(context),
        ],
      ),
    );
  }

  Widget _buildHeader(BuildContext context) {
    if (isNewPresetDraft && nameController != null) {
      return TextField(
        controller: nameController,
        style: TextStyle(color: context.themeColors.textBody),
        decoration: InputDecoration(
          hintText: 'Название нового пресета',
          hintStyle:
              TextStyle(color: context.themeColors.textBody.withValues(alpha: 0.5)),
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(8),
            borderSide: BorderSide(
              color: context.themeColors.primary.withValues(alpha: 0.3),
            ),
          ),
        ),
      );
    }

    final title = presetName ?? 'Создайте новый пресет';
    return Row(
      children: [
        if (isActive)
          Padding(
            padding: const EdgeInsets.only(right: 6),
            child: Icon(Icons.star, size: 18, color: context.themeColors.primary),
          ),
        Expanded(
          child: Text(
            title,
            style: context.textStyle.textSettings,
            overflow: TextOverflow.ellipsis,
          ),
        ),
      ],
    );
  }

  Widget _buildSaveButton(BuildContext context) {
    return TextButton(
      onPressed: isSaveEnabled ? onSave : null,
      style: ButtonStyle(
        foregroundColor:
            WidgetStatePropertyAll(context.themeColors.textButtonSelected),
        backgroundColor: WidgetStatePropertyAll(context.themeColors.primary),
      ),
      child: const Text('Сохранить'),
    );
  }
}
