import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/models/manual_settings.dart';
import 'package:autoheat/src/extensions/context_extensions.dart';
import 'package:autoheat/src/presentation/screens/heat/components/manual_settings_section.dart';
import 'package:flutter/material.dart';

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
        // gradient: LinearGradient(
        //   begin: Alignment.topCenter,
        //   end: Alignment.bottomCenter,
        //   colors: [
        //     Colors.black,
        //     Colors.black.withValues(alpha: 0.8),
        //     context.themeColors.primary.withValues(alpha: 0.1),
        //   ],
        // ),
        borderRadius: BorderRadius.circular(50),
      ),
      child: IntrinsicHeight(
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(
              child: Padding(
                padding: const EdgeInsets.all(16),
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
            ),
            Container(
              color: context.themeColors.primary.withAlpha(70),
              width: 2,
            ),
            Expanded(
              child: Padding(
                padding: const EdgeInsets.all(16),
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
            ),
          ],
        ),
      ),
    );
  }
}
