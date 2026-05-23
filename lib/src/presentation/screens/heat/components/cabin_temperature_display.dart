// FILE: lib/src/presentation/screens/heat/components/cabin_temperature_display.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Отображение температуры салона на HeatScreen.
//   SCOPE: visibility gate через SettingsCubit, чтение CabinTemperatureCubit state,
//          цветовая индикация температуры.
//   DEPENDS: M-UI-HEAT, M-CABIN-TEMPERATURE, M-SETTINGS, M-THEME
//   LINKS: V-M-UI-HEAT, V-M-CABIN-TEMPERATURE, FA-003
//   ROLE: RUNTIME
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   CabinTemperatureDisplay - StatelessWidget indicator for cabin temperature
//   build - SettingsCubit visibility + CabinTemperatureCubit value
//   _buildTemperatureContainer - common decorated row
//   _getTemperatureColor - temp bands to ColorConstants
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.0.0 - Phase-4 Slice-3: UI читает CabinTemperatureCubit]
// END_CHANGE_SUMMARY

import 'package:autoheat/src/config/color_constants.dart';
import 'package:autoheat/src/cubit/cabin_temperature_cubit.dart';
import 'package:autoheat/src/cubit/settings_cubit.dart';
import 'package:autoheat/src/extensions/context_extensions.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

class CabinTemperatureDisplay extends StatelessWidget {
  const CabinTemperatureDisplay({super.key});

  @override
  Widget build(BuildContext context) {
    return BlocBuilder<SettingsCubit, SettingsState>(
      builder: (context, settingsState) {
        if (settingsState.showCabinTemperature) {
          return BlocBuilder<CabinTemperatureCubit, CabinTemperatureState>(
            builder: (context, temperatureState) {
              return _buildTemperatureContainer(
                context,
                temperatureState.celsius,
              );
            },
          );
        }
        return Container(
          margin: const EdgeInsets.only(bottom: 20),
          height: 56,
        );
      },
    );
  }

  Widget _buildTemperatureContainer(BuildContext context, double? cabinTemp) {
    return Container(
      margin: const EdgeInsets.only(bottom: 20),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: context.themeColors.primary.withAlpha(30),
        borderRadius: BorderRadius.circular(50),
        border: Border.all(
          color: context.themeColors.primary.withAlpha(100),
          width: 1,
        ),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            Icons.thermostat,
            color: context.themeColors.primary,
            size: 24,
          ),
          const SizedBox(width: 8),
          Text(
            'Температура в салоне: ',
            style: context.textStyle.paragraph1.copyWith(
              color: context.themeColors.textBody,
            ),
          ),
          Text(
            '${cabinTemp?.toStringAsFixed(1) ?? "--"} °C',
            style: context.textStyle.paragraph1.copyWith(
              fontWeight: FontWeight.bold,
              color: _getTemperatureColor(cabinTemp ?? 0),
            ),
          ),
        ],
      ),
    );
  }

  Color _getTemperatureColor(double temp) => switch (temp) {
        <= -5 => ColorConstants.systemBlue,
        <= 5 => ColorConstants.systemLightBlue,
        <= 25 => ColorConstants.systemOrange,
        _ => ColorConstants.accentRed,
      };
}
