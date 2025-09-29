import 'package:autoheat/src/config/color_constants.dart';
import 'package:autoheat/src/cubit/mode_cubit.dart';
import 'package:autoheat/src/cubit/mode_state_cubit.dart';
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
          return BlocBuilder<ModeCubit, ModesState>(
            builder: (context, modeState) {
              final cubit = context.read<ModeCubit>();
              final cabinTemp = cubit.cabinTemperature;

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

  Color _getTemperatureColor(double temp) => switch (temp) {
        <= -5 => ColorConstants.systemBlue,
        <= 5 => ColorConstants.systemLightBlue,
        <= 25 => ColorConstants.systemOrange,
        _ => ColorConstants.accentRed,
      };
}
