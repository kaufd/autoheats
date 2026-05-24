// FILE: lib/src/presentation/screens/presets/components/preset_editor.dart
// VERSION: 1.1.0
// START_MODULE_CONTRACT
//   PURPOSE: Editor pane для одного пресета: header (title или active marker) + threshold/level sliders + Save button.
//   SCOPE: read-only props + onChange callbacks, no Bloc access, parent owns state.
//   DEPENDS: M-UI-PRESETS, M-MANUAL-SETTINGS, M-ENUMS, M-THEME
//   LINKS: M-UI-PRESETS, DF-PRESET-APPLY, FA-001
//   ROLE: RUNTIME
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   PresetEditor - StatelessWidget: header + sliders + Save aligned center
//   _buildHeader - title row with optional star marker
//   _buildSaveButton - filled TextButton; disabled when isSaveEnabled=false
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.1.0 - Drop inline name TextField; name запрашивается через SavePresetDialog после Save]
//   PREVIOUS_CHANGE: [v1.0.0 - Initial PresetEditor for merged Presets tab]
// END_CHANGE_SUMMARY

import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/extensions/context_extensions.dart';
import 'package:autoheat/src/models/manual_settings.dart';
import 'package:autoheat/src/presentation/screens/settings/components/manual_settings_section.dart';
import 'package:flutter/material.dart';

class PresetEditor extends StatelessWidget {
  final UserType userType;
  final ManualHeatSettings settings;
  final String? presetName; // null → empty/idle state
  final bool isActive; // true когда показанный пресет является selectedPresets[user]
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
          // Spacer прижимает Save к низу колонки — расстояние от низа совпадает
          // с «Новый пресет» в PresetList (оба внутри Padding(all:16)).
          const Spacer(),
          Align(
            alignment: Alignment.center,
            child: _buildSaveButton(context),
          ),
        ],
      ),
    );
  }

  Widget _buildHeader(BuildContext context) {
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
        padding: const WidgetStatePropertyAll(
          EdgeInsets.symmetric(horizontal: 32, vertical: 12),
        ),
      ),
      child: const Text('Сохранить'),
    );
  }
}
