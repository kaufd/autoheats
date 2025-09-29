import 'package:autoheat/src/cubit/manual_settings_cubit.dart';
import 'package:autoheat/src/models/manual_settings.dart';
import 'package:autoheat/src/config/color_constants.dart';
import 'package:autoheat/src/extensions/context_extensions.dart';
import 'package:autoheat/src/presentation/screens/settings/components/presets_settings.dart';
import 'package:autoheat/src/presentation/ui/error_block.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

class PresetsSection extends StatelessWidget {
  const PresetsSection({super.key});

  @override
  Widget build(BuildContext context) {
    return BlocBuilder<ManualSettingsCubit, ManualSettingsState>(
      builder: (context, settingsState) {
        if (settingsState.isLoading) {
          return Center(
            child: Padding(
              padding: const EdgeInsets.all(20.0),
              child: CircularProgressIndicator(color: context.themeColors.primary),
            ),
          );
        }

        if (settingsState.error != null) {
          return const ErrorBlock(
            message: 'Ошибка загрузки настроек',
          );
        }

        return Container(
          decoration: BoxDecoration(
            color: ColorConstants.systemBlack.withValues(alpha: 0.8),
            borderRadius: BorderRadius.circular(35),
            border: Border.all(
              color: context.themeColors.primary.withValues(alpha: 0.3),
              width: 1,
            ),
          ),
          child: PresetsSettings(
            settingsState: settingsState,
            onAutoHeatLevelChanged: (autoHeatLevel, level, userType) {
              context.read<ManualSettingsCubit>().updateAutoHeatLevel(
                    userType,
                    autoHeatLevel,
                    level,
                  );
            },
            onTemperatureThresholdChanged: (temperature, userType) {
              context.read<ManualSettingsCubit>().updateTemperatureThreshold(
                    userType,
                    temperature,
                  );
            },
          ),
        );
      },
    );
  }
}
