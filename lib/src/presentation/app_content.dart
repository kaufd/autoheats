// FILE: lib/src/presentation/app_content.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Корневой UI-контейнер с табами Heat/Settings/Presets и применением пресетов.
//   SCOPE: TabController navigation, themed background, DF-PRESET-APPLY bridge to ModeCubit.
//   DEPENDS: M-UI-HEAT, M-UI-SETTINGS, M-UI-PRESETS, M-THEME, M-PRESET, M-MODE
//   LINKS: M-UI-APP, M-UI-PRESETS, M-PRESET, M-MODE, DF-PRESET-APPLY, FA-001, FA-011
//   ROLE: RUNTIME
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   AppContent - StatefulWidget с TabController на 3 вкладки
//   initState/dispose - lifecycle TabController
//   _selectTab - перейти на вкладку
//   build - Scaffold/AppBar/TabBarView с themed background
//   _buildTabButton - nav button для вкладки
//   _applyPreset - сохранить ManualSettings и применить Preset runtime через ModeCubit/HVAC
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.1.0 - Phase-4 Slice-1: preset apply delegates to ModeCubit.applyPreset]
// END_CHANGE_SUMMARY

import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/cubit/manual_settings_cubit.dart';
import 'package:autoheat/src/cubit/mode_cubit.dart';
import 'package:autoheat/src/cubit/preset_cubit.dart';
import 'package:autoheat/src/extensions/context_extensions.dart';
import 'package:autoheat/src/models/preset.dart';
import 'package:autoheat/src/presentation/screens/heat/heat_screen.dart';
import 'package:autoheat/src/presentation/screens/settings/settings_screen.dart';
import 'package:autoheat/src/presentation/screens/presets/presets_list_screen.dart';
import 'package:autoheat/src/presentation/themes/theme_cubit.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

class AppContent extends StatefulWidget {
  const AppContent({super.key});

  @override
  AppContentState createState() => AppContentState();
}

class AppContentState extends State<AppContent>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;
  int _selectedIndex = 0;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 3, vsync: this);

    _tabController.addListener(() {
      if (_tabController.index != _selectedIndex) {
        setState(() {
          _selectedIndex = _tabController.index;
        });
      }
    });
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  void _selectTab(int index) {
    setState(() {
      _selectedIndex = index;
    });
    _tabController.animateTo(index);
  }

  @override
  Widget build(BuildContext context) {
    final String themeName = context.read<ThemeCubit>().state.key;

    return Scaffold(
      appBar: AppBar(
        title: Row(
          children: [
            Padding(
              padding: const EdgeInsets.only(left: 32),
              child: Text(
                'Подогрев сидений',
                style: context.textStyle.textNavActive
                    .copyWith(color: context.themeColors.textButtonPrimary),
              ),
            ),
            Container(
              margin: EdgeInsets.symmetric(horizontal: 24),
              height: 28,
              width: 1,
              color: Colors.white,
            ),
            _buildTabButton('Управление', 0, _selectTab),
            _buildTabButton('Настройки', 1, _selectTab),
            _buildTabButton('Пресеты', 2, _selectTab),
            Expanded(child: SizedBox.shrink()),
          ],
        ),
      ),
      body: SafeArea(
        child: Container(
          decoration: BoxDecoration(
            image: DecorationImage(
              image: AssetImage('assets/images/background_$themeName.png'),
              fit: BoxFit.cover,
            ),
          ),
          child: Padding(
            padding: const EdgeInsets.fromLTRB(32, 46, 32, 32),
            child: TabBarView(
              controller: _tabController,
              children: [
                HeatScreen(),
                SettingsScreen(),
                PresetsListScreen(
                  onPresetApplied: (preset) {
                    _applyPreset(preset);
                  },
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildTabButton(
      String text, int index, void Function(int index) selectTab) {
    final isActiveTab = _selectedIndex == index;

    return TextButton(
      style: ButtonStyle(
        backgroundColor: WidgetStatePropertyAll(
          isActiveTab
              ? context.themeColors.backgroundButtonPrimary
              : Colors.transparent,
        ),
      ),
      onPressed: () => selectTab(index),
      child: Text(
        text,
        style: isActiveTab
            ? context.textStyle.textNavActive
            : context.textStyle.textNav,
      ),
    );
  }

  // START_CONTRACT: _applyPreset
  //   PURPOSE: Применить Preset к manual-settings state и runtime ModeCubit/HVAC.
  //   INPUTS: { preset: Preset }
  //   OUTPUTS: { Future<void> }
  //   SIDE_EFFECTS: ManualSettings persistence, ModeCubit.applyPreset, PresetCubit.applyPreset, SnackBar.
  //   LINKS: M-UI-PRESETS, M-PRESET, M-MODE, M-HVAC, DF-PRESET-APPLY, FA-001, FA-011
  // END_CONTRACT: _applyPreset
  Future<void> _applyPreset(Preset preset) async {
    // START_BLOCK_APPLY_PRESET
    final manualSettingsCubit = context.read<ManualSettingsCubit>();
    final modeCubit = context.read<ModeCubit>();
    final presetCubit = context.read<PresetCubit>();

    if (preset.userType == UserType.driver) {
      final currentState = manualSettingsCubit.state;
      await manualSettingsCubit.applyPresetSettings(
        preset.settings,
        currentState.passengerSettings,
      );
    } else {
      final currentState = manualSettingsCubit.state;
      await manualSettingsCubit.applyPresetSettings(
        currentState.driverSettings,
        preset.settings,
      );
    }

    await modeCubit.applyPreset(preset);
    await presetCubit.applyPreset(preset);

    if (!mounted) return;

    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          'Пресет "${preset.name}" применен для ${preset.userType == UserType.driver ? 'водителя' : 'пассажира'}',
        ),
        backgroundColor: context.themeColors.primary,
      ),
    );
    // END_BLOCK_APPLY_PRESET
  }
}
