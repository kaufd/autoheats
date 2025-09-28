import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/models/manual_settings.dart';
import 'package:autoheat/src/extensions/context_extensions.dart';
import 'package:autoheat/src/presentation/screens/settings/components/manual_settings_section.dart';
import 'package:autoheat/src/presentation/screens/settings/components/save_preset_dialog.dart';
import 'package:autoheat/src/cubit/preset_cubit.dart';
import 'package:autoheat/src/cubit/manual_settings_cubit.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

class PresetsScreen extends StatelessWidget {
  final ManualSettingsState settingsState;
  final Function(AutoHeatLevel, int, UserType) onAutoHeatLevelChanged;
  final Function(double, UserType) onTemperatureThresholdChanged;

  const PresetsScreen({
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
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  children: [
                    Expanded(
                      child: ManualSettingsSection(
                        userType: UserType.driver,
                        settings: settingsState.driverSettings,
                        onAutoHeatLevelChanged: (autoHeatLevel, level) {
                          onAutoHeatLevelChanged(autoHeatLevel, level, UserType.driver);
                        },
                        onTemperatureThresholdChanged: (temperature) {
                          onTemperatureThresholdChanged(temperature, UserType.driver);
                        },
                      ),
                    ),
                    const SizedBox(height: 16),
                    _buildSavePresetButton(context, UserType.driver),
                  ],
                ),
              ),
            ),
            Container(
              color: context.themeColors.primary.withAlpha(70),
              width: 2,
            ),
            Expanded(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  children: [
                    Expanded(
                      child: ManualSettingsSection(
                        userType: UserType.passenger,
                        settings: settingsState.passengerSettings,
                        onAutoHeatLevelChanged: (autoHeatLevel, level) {
                          onAutoHeatLevelChanged(autoHeatLevel, level, UserType.passenger);
                        },
                        onTemperatureThresholdChanged: (temperature) {
                          onTemperatureThresholdChanged(temperature, UserType.passenger);
                        },
                      ),
                    ),
                    const SizedBox(height: 16),
                    _buildSavePresetButton(context, UserType.passenger),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSavePresetButton(BuildContext context, UserType userType) {
    return SizedBox(
      width: double.infinity,
      child: ElevatedButton.icon(
        onPressed: () => _savePresetForUser(context, userType),
        icon: const Icon(Icons.save),
        label:
            Text('Сохранить пресет для ${userType == UserType.driver ? 'водителя' : 'пассажира'}'),
        style: ElevatedButton.styleFrom(
          backgroundColor: context.themeColors.primary,
          foregroundColor: Colors.white,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(8),
          ),
        ),
      ),
    );
  }

  Future<void> _savePresetForUser(BuildContext context, UserType userType) async {
    final result = await showDialog<String>(
      context: context,
      builder: (context) => const SavePresetDialog(),
    );

    if (result != null && context.mounted) {
      final manualSettingsState = context.read<ManualSettingsCubit>().state;
      final settings = userType == UserType.driver
          ? manualSettingsState.driverSettings
          : manualSettingsState.passengerSettings;

      context.read<PresetCubit>().savePreset(
            name: result,
            userType: userType,
            settings: settings,
          );

      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
              'Пресет "$result" сохранен для ${userType == UserType.driver ? 'водителя' : 'пассажира'}'),
          backgroundColor: context.themeColors.primary,
        ),
      );
    }
  }
}
