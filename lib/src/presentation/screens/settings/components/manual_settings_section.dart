import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/models/manual_settings.dart';
import 'package:autoheat/src/presentation/screens/settings/components/auto_heat_level_slider.dart';
import 'package:autoheat/src/presentation/screens/settings/components/temperature_threshold_slider.dart';
import 'package:flutter/material.dart';

class ManualSettingsSection extends StatelessWidget {
  final UserType userType;
  final ManualHeatSettings settings;
  final Function(AutoHeatLevel, int) onAutoHeatLevelChanged;
  final Function(double) onTemperatureThresholdChanged;

  const ManualSettingsSection({
    super.key,
    required this.userType,
    required this.settings,
    required this.onAutoHeatLevelChanged,
    required this.onTemperatureThresholdChanged,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        ...settings.autoHeatLevels.asMap().entries.map((entry) {
          final index = entry.key;
          final autoHeatLevel = entry.value;

          final durationLabel = switch (index) {
            0 => 'Уровень 1',
            1 => 'Уровень 2',
            2 => 'Уровень 3',
            _ => throw StateError('Unexpected index: $index'),
          };

          return Padding(
            padding: const EdgeInsets.symmetric(vertical: 16),
            child: AutoHeatLevelSlider(
              autoHeatLevel: autoHeatLevel,
              durationLabel: durationLabel,
              levelIndex: index,
              onLevelChanged: (duration) {
                onAutoHeatLevelChanged(autoHeatLevel, duration);
              },
            ),
          );
        }),
        const SizedBox(height: 16),
        TemperatureThresholdSlider(
          temperatureThreshold: settings.temperatureThreshold,
          onTemperatureChanged: onTemperatureThresholdChanged,
        ),
      ],
    );
  }
}
