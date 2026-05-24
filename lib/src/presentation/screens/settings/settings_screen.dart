// FILE: lib/src/presentation/screens/settings/settings_screen.dart
// VERSION: 1.2.0
// START_MODULE_CONTRACT
//   PURPOSE: Глобальные настройки приложения: тема и видимость температуры салона.
//   SCOPE: ThemeSelector, CustomSwitch для cabin-temperature visibility. Все настройки пресетов
//          переехали в PresetsTab; этот экран теперь slim и не использует ManualSettingsCubit.
//   DEPENDS: M-UI-SETTINGS, M-SETTINGS, M-THEME
//   LINKS: M-UI-SETTINGS, V-M-UI-SETTINGS, FA-009
//   ROLE: RUNTIME
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   SettingsScreen - StatelessWidget: theme row + cabin-temperature visibility row
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.2.0 - Slim settings: preset config moved to PresetsTab, ManualSettingsCubit init removed]
//   PREVIOUS_CHANGE: [v1.1.0 - Phase-4 Slice-6: GRACE contract and head-unit layout smoke coverage]
// END_CHANGE_SUMMARY

import 'package:autoheat/src/cubit/settings_cubit.dart';
import 'package:autoheat/src/extensions/context_extensions.dart';
import 'package:autoheat/src/presentation/screens/settings/components/theme_selector.dart';
import 'package:autoheat/src/presentation/ui/custom_switch.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.start,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'Тема приложения: ',
                style: context.textStyle.textSettings,
              ),
              const Expanded(child: ThemeSelector()),
            ],
          ),
          const SizedBox(height: 20),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'Показывать температуру в салоне: ',
                style: context.textStyle.textSettings,
              ),
              BlocBuilder<SettingsCubit, SettingsState>(
                builder: (context, settingsState) {
                  return CustomSwitch(
                    value: settingsState.showCabinTemperature,
                    onChanged: (value) async {
                      await context
                          .read<SettingsCubit>()
                          .setCabinTemperatureVisibility(value);
                    },
                  );
                },
              ),
            ],
          ),
        ],
      ),
    );
  }
}
