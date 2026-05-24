// FILE: lib/src/presentation/screens/settings/components/presets_settings.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: UI-секция сохранения driver/passenger preset snapshots из текущих настроек.
//   SCOPE: два ManualSettingsSection, кнопки сохранения, SavePresetDialog,
//          snapshot runtime mode/level из ModeCubit.
//   DEPENDS: M-PRESET, M-MANUAL-SETTINGS, M-MODE, M-UI-SETTINGS
//   LINKS: M-UI-SETTINGS, M-PRESET, FA-001, FA-011, DF-PRESET-APPLY
//   ROLE: RUNTIME
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   PresetsSettings - layout настроек пресетов driver/passenger
//   _buildManualSettingsColumn - вертикальная колонка без flex child под scrollable
//   _buildSavePresetButton - кнопка сохранения для UserType
//   _savePresetForUser - диалог имени + PresetCubit.savePreset с mode/level snapshot
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.3.0 - White theme save button uses selected foreground token]
//   PREVIOUS_CHANGE: [v1.2.0 - Phase-4 Slice-6: remove unbounded vertical Expanded in settings layout]
// END_CHANGE_SUMMARY

import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/cubit/mode_cubit.dart';
import 'package:autoheat/src/models/manual_settings.dart';
import 'package:autoheat/src/extensions/context_extensions.dart';
import 'package:autoheat/src/presentation/screens/settings/components/manual_settings_section.dart';
import 'package:autoheat/src/presentation/screens/settings/components/save_preset_dialog.dart';
import 'package:autoheat/src/cubit/preset_cubit.dart';
import 'package:autoheat/src/cubit/manual_settings_cubit.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

class PresetsSettings extends StatelessWidget {
  final ManualSettingsState settingsState;
  final Function(AutoHeatLevel, int, UserType) onAutoHeatLevelChanged;
  final Function(double, UserType) onTemperatureThresholdChanged;

  const PresetsSettings({
    super.key,
    required this.settingsState,
    required this.onAutoHeatLevelChanged,
    required this.onTemperatureThresholdChanged,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(50),
      ),
      child: IntrinsicHeight(
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(
              child: _buildManualSettingsColumn(context, UserType.driver),
            ),
            Container(
              color: context.themeColors.primary.withAlpha(70),
              width: 2,
            ),
            Expanded(
              child: _buildManualSettingsColumn(context, UserType.passenger),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildManualSettingsColumn(BuildContext context, UserType userType) {
    final isDriver = userType == UserType.driver;

    return Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        children: [
          ManualSettingsSection(
            userType: userType,
            settings: isDriver
                ? settingsState.driverSettings
                : settingsState.passengerSettings,
            onAutoHeatLevelChanged: (autoHeatLevel, level) {
              onAutoHeatLevelChanged(autoHeatLevel, level, userType);
            },
            onTemperatureThresholdChanged: (temperature) {
              onTemperatureThresholdChanged(temperature, userType);
            },
          ),
          const SizedBox(height: 16),
          _buildSavePresetButton(context, userType),
        ],
      ),
    );
  }

  Widget _buildSavePresetButton(BuildContext context, UserType userType) {
    return TextButton(
      onPressed: () => _savePresetForUser(context, userType),
      style: ButtonStyle(
        foregroundColor:
            WidgetStatePropertyAll(context.themeColors.textButtonSelected),
        backgroundColor: WidgetStatePropertyAll(context.themeColors.primary),
        side: WidgetStatePropertyAll(
          BorderSide(color: context.themeColors.primary, width: 0.5),
        ),
      ),
      child: const Text('Сохранить'),
    );
  }

  Future<void> _savePresetForUser(
      BuildContext context, UserType userType) async {
    final result = await showDialog<String>(
      context: context,
      builder: (context) => const SavePresetDialog(),
    );

    if (result != null && context.mounted) {
      final manualSettingsState = context.read<ManualSettingsCubit>().state;
      final settings = userType == UserType.driver
          ? manualSettingsState.driverSettings
          : manualSettingsState.passengerSettings;
      final modeCubit = context.read<ModeCubit>();
      final heatMode =
          HeatModeExtension.fromString(modeCubit.getModeByUser(userType));
      final heatLevel = modeCubit.getHeatLevelByUser(userType);

      await context.read<PresetCubit>().savePreset(
            name: result,
            userType: userType,
            settings: settings,
            heatMode: heatMode,
            heatLevel: heatLevel,
          );

      if (!context.mounted) return;

      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
              'Пресет "$result" сохранен для ${userType == UserType.driver ? 'водителя' : 'пассажира'}'),
          backgroundColor: context.themeColors.primary.withValues(alpha: 0.8),
        ),
      );
    }
  }
}
