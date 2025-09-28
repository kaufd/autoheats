import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/models/manual_settings.dart';
import 'package:autoheat/src/presentation/screens/heat/components/auto_heat_level_slider.dart';
import 'package:autoheat/src/presentation/screens/heat/components/temperature_threshold_slider.dart';
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

          String durationLabel;
          switch (index) {
            case 0:
              durationLabel = 'Уровень 1';
              break;
            case 1:
              durationLabel = 'Уровень 2';
              break;
            case 2:
              durationLabel = 'Уровень 3';
              break;
            default:
              durationLabel = 'Уровень ${index + 1}';
          }

          return Padding(
            padding: const EdgeInsets.only(bottom: 12),
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

        const SizedBox(height: 16),

        // Center(
        //   child: Text(
        //     userType == UserType.driver ? 'Водитель' : 'Пассажир',
        //     style: Theme.of(context).textTheme.bodyMedium?.copyWith(
        //           color: Colors.white,
        //           fontWeight: FontWeight.w600,
        //         ),
        //   ),
        // ),
      ],
    );
  }
}
