import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/models/manual_settings.dart';
import 'package:autoheat/src/extensions/context_extensions.dart';
import 'package:autoheat/src/presentation/screens/heat/components/manual_settings_section.dart';
import 'package:autoheat/src/presentation/screens/heat/components/cabin_temperature_display.dart';
import 'package:flutter/material.dart';

class ManualSettingsScreen extends StatelessWidget {
  final ManualSettingsState settingsState;
  final Function(AutoHeatLevel, int, UserType) onAutoHeatLevelChanged;
  final Function(double, UserType) onTemperatureThresholdChanged;

  const ManualSettingsScreen({
    super.key,
    required this.settingsState,
    required this.onAutoHeatLevelChanged,
    required this.onTemperatureThresholdChanged,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        const CabinTemperatureDisplay(),
        Expanded(
          child: Container(
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                colors: [
                  Colors.black,
                  Colors.black.withValues(alpha: 0.8),
                  context.themeColors.primary.withValues(alpha: 0.1),
                ],
              ),
            ),
            child: IntrinsicWidth(
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  SizedBox(
                    width: (MediaQuery.of(context).size.width - 66) / 2,
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
                    height: 400,
                    color: context.themeColors.primary.withAlpha(70),
                    width: 2,
                  ),
                  SizedBox(
                    width: (MediaQuery.of(context).size.width - 66) / 2,
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
          ),
        ),
      ],
    );
  }
}
