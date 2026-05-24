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
//   _applyPreset - применить Preset через ModeCubit и PresetCubit
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.3.0 - Mode-source decoupling: onPresetsSegmentTapped routes to apply or tab navigation]
//   PREVIOUS_CHANGE: [v1.2.0 - Settings/Presets UX redesign: tab order Управление→Пресеты→Настройки, merged PresetsTab]
// END_CHANGE_SUMMARY

import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/cubit/mode_cubit.dart';
import 'package:autoheat/src/cubit/preset_cubit.dart';
import 'package:autoheat/src/extensions/context_extensions.dart';
import 'package:autoheat/src/models/preset.dart';
import 'package:autoheat/src/presentation/screens/heat/heat_screen.dart';
import 'package:autoheat/src/presentation/screens/settings/settings_screen.dart';
import 'package:autoheat/src/presentation/screens/presets/presets_tab.dart';
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

    // Прогреваем PresetCubit до первого взаимодействия с ModeToggler:
    // _onPresetsSegmentTapped читает state.selectedPresets, и без этой загрузки
    // на холодном старте всегда виден «пресет не выбран» → нас бросает на таб
    // «Пресеты», хотя ModeCubit уже работает в режиме presets с активным id.
    context.read<PresetCubit>().loadAllPresets();
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

  void _onPresetsSegmentTapped(UserType user) {
    final presetCubit = context.read<PresetCubit>();
    final activePreset = presetCubit.state.selectedPresets[user];
    if (activePreset == null) {
      _selectTab(1); // tab «Пресеты»
      return;
    }
    // С активным пресетом — обычный apply flow (тот же, что у ▶ apply в списке).
    _applyPreset(activePreset);
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
            _buildTabButton('Пресеты', 1, _selectTab),
            _buildTabButton('Настройки', 2, _selectTab),
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
                HeatScreen(onPresetsSegmentTapped: _onPresetsSegmentTapped),
                PresetsTab(
                  onPresetApplied: (preset) {
                    _applyPreset(preset);
                  },
                ),
                SettingsScreen(),
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
  //   PURPOSE: Применить пользовательский Preset к runtime через ModeCubit и PresetCubit.
  //   INPUTS: { preset: Preset }
  //   OUTPUTS: { Future<void> }
  //   SIDE_EFFECTS: ModeCubit.applyPreset (mode=presets + AutoHeatService restart),
  //                 PresetCubit.applyPreset (selectedPresetId + lastUsed), SnackBar.
  //   LINKS: M-UI-PRESETS, M-PRESET, M-MODE, M-HVAC, DF-PRESET-APPLY, FA-001, FA-011
  // END_CONTRACT: _applyPreset
  Future<void> _applyPreset(Preset preset) async {
    // START_BLOCK_APPLY_PRESET
    final modeCubit = context.read<ModeCubit>();
    final presetCubit = context.read<PresetCubit>();

    await modeCubit.applyPreset(preset);
    await presetCubit.applyPreset(preset);

    if (!mounted) return;

    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          'Пресет "${preset.name}" применен для ${preset.userType == UserType.driver ? 'водителя' : 'пассажира'}',
          style: TextStyle(color: context.themeColors.textButtonSelected),
        ),
        backgroundColor: context.themeColors.primary,
      ),
    );
    // END_BLOCK_APPLY_PRESET
  }
}
