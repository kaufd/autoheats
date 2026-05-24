// FILE: lib/src/presentation/app_content.dart
// VERSION: 1.4.0
// START_MODULE_CONTRACT
//   PURPOSE: Корневой UI-контейнер с табами Heat/Settings/Presets и применением пресетов.
//   SCOPE: TabController navigation, themed background, DF-PRESET-APPLY bridge to ModeCubit,
//          dynamic debug tab (Логи + sidebar-injector) при включённом SettingsCubit.debugMode.
//   DEPENDS: M-UI-HEAT, M-UI-SETTINGS, M-UI-PRESETS, M-THEME, M-PRESET, M-MODE, M-SETTINGS
//   LINKS: M-UI-APP, M-UI-PRESETS, M-PRESET, M-MODE, DF-PRESET-APPLY, FA-001, FA-011
//   ROLE: RUNTIME
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   AppContent - StatefulWidget с TabController на 3 или 4 вкладки (зависит от debugMode)
//   initState/dispose - lifecycle TabController, обновление debug-табов через _rebuildTabController
//   _selectTab - перейти на вкладку
//   _rebuildTabController - пересоздать TabController при смене debugMode
//   _tabs / _tabLabels - формируют контент и подписи в зависимости от debugMode
//   build - Scaffold/AppBar/TabBarView с themed background; BlocListener реагирует на debugMode
//   _buildTabButton - nav button для вкладки
//   _applyPreset - применить Preset через ModeCubit и PresetCubit
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.4.0 - Dynamic debug tabs (Температура/Логи) под SettingsCubit.debugMode]
//   PREVIOUS_CHANGE: [v1.3.0 - Mode-source decoupling: onPresetsSegmentTapped routes to apply or tab navigation]
// END_CHANGE_SUMMARY

import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/cubit/mode_cubit.dart';
import 'package:autoheat/src/cubit/preset_cubit.dart';
import 'package:autoheat/src/cubit/settings_cubit.dart';
import 'package:autoheat/src/extensions/context_extensions.dart';
import 'package:autoheat/src/models/preset.dart';
import 'package:autoheat/src/presentation/screens/debug/logs_screen.dart';
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
    with TickerProviderStateMixin {
  TabController? _tabController;
  int _selectedIndex = 0;
  bool _debugMode = false;

  @override
  void initState() {
    super.initState();
    _debugMode = context.read<SettingsCubit>().state.debugMode;
    _rebuildTabController(_debugMode, initial: true);

    // Прогреваем PresetCubit до первого взаимодействия с ModeToggler:
    // _onPresetsSegmentTapped читает state.selectedPresets, и без этой загрузки
    // на холодном старте всегда виден «пресет не выбран» → нас бросает на таб
    // «Пресеты», хотя ModeCubit уже работает в режиме presets с активным id.
    context.read<PresetCubit>().loadAllPresets();
  }

  @override
  void dispose() {
    _tabController?.dispose();
    super.dispose();
  }

  void _rebuildTabController(bool debugMode, {bool initial = false}) {
    final newLength = debugMode ? 4 : 3;
    final previous = _tabController;
    final restoredIndex =
        (previous?.index ?? 0).clamp(0, newLength - 1);
    final controller = TabController(
      length: newLength,
      vsync: this,
      initialIndex: restoredIndex,
    );
    controller.addListener(() {
      if (controller.index != _selectedIndex) {
        setState(() {
          _selectedIndex = controller.index;
        });
      }
    });
    if (initial) {
      _tabController = controller;
      _selectedIndex = restoredIndex;
    } else {
      setState(() {
        _tabController = controller;
        _selectedIndex = restoredIndex;
        _debugMode = debugMode;
      });
    }
    previous?.dispose();
  }

  void _selectTab(int index) {
    final controller = _tabController;
    if (controller == null) return;
    setState(() {
      _selectedIndex = index;
    });
    controller.animateTo(index);
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

  List<String> get _tabLabels {
    return [
      'Управление',
      'Пресеты',
      'Настройки',
      if (_debugMode) 'Логи',
    ];
  }

  List<Widget> _tabs() {
    return [
      HeatScreen(onPresetsSegmentTapped: _onPresetsSegmentTapped),
      PresetsTab(
        onPresetApplied: (preset) {
          _applyPreset(preset);
        },
      ),
      const SettingsScreen(),
      // Один debug-таб: «Логи» с sidebar-инжектором температуры.
      if (_debugMode) const LogsScreen(),
    ];
  }

  @override
  Widget build(BuildContext context) {
    final String themeName = context.read<ThemeCubit>().state.key;
    final controller = _tabController;
    final labels = _tabLabels;

    return BlocListener<SettingsCubit, SettingsState>(
      listenWhen: (prev, curr) => prev.debugMode != curr.debugMode,
      listener: (context, state) {
        _rebuildTabController(state.debugMode);
      },
      child: Scaffold(
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
                margin: const EdgeInsets.symmetric(horizontal: 24),
                height: 28,
                width: 1,
                color: Colors.white,
              ),
              for (int i = 0; i < labels.length; i++)
                _buildTabButton(labels[i], i, _selectTab),
              const Expanded(child: SizedBox.shrink()),
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
              child: controller == null
                  ? const SizedBox.shrink()
                  : TabBarView(
                      controller: controller,
                      children: _tabs(),
                    ),
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
