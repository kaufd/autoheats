// FILE: lib/src/presentation/screens/settings/settings_screen.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Контейнер экрана настроек: пресеты, тема, видимость температуры салона.
//   SCOPE: initialize ManualSettingsCubit, compose PresetsSection, ThemeSelector, SettingsCubit switch.
//   DEPENDS: M-UI-SETTINGS, M-MANUAL-SETTINGS, M-SETTINGS, M-THEME
//   LINKS: M-UI-SETTINGS, V-M-UI-SETTINGS, FA-009
//   ROLE: RUNTIME
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   SettingsScreen - StatefulWidget settings container
//   _SettingsScreenState.initState - ManualSettingsCubit.initialize
//   _SettingsScreenState.build - scrollable settings layout safe for head-unit constraints
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.1.0 - Phase-4 Slice-6: GRACE contract and head-unit layout smoke coverage]
// END_CHANGE_SUMMARY

import 'package:autoheat/src/cubit/settings_cubit.dart';
import 'package:autoheat/src/cubit/manual_settings_cubit.dart';
import 'package:autoheat/src/presentation/screens/settings/components/presets_section.dart';
import 'package:autoheat/src/presentation/ui/custom_switch.dart';
import 'package:autoheat/src/presentation/screens/settings/components/theme_selector.dart';
import 'package:autoheat/src/extensions/context_extensions.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  @override
  void initState() {
    super.initState();
    context.read<ManualSettingsCubit>().initialize();
  }

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  'Настройки пресетов:',
                  style: context.textStyle.textSettings,
                ),
              ],
            ),
            const SizedBox(height: 12),
            const PresetsSection(),
            const SizedBox(height: 20),
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
      ),
    );
  }
}
