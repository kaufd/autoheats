import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/cubit/manual_settings_cubit.dart';
import 'package:autoheat/src/cubit/preset_cubit.dart';
import 'package:autoheat/src/extensions/context_extensions.dart';
import 'package:autoheat/src/presentation/screens/heat/heat_screen.dart';
import 'package:autoheat/src/presentation/screens/settings/settings_screen.dart';
import 'package:autoheat/src/presentation/screens/presets/presets_list_screen.dart';
import 'package:autoheat/src/presentation/themes/theme_cubit.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:provider/provider.dart';

class AppContent extends StatefulWidget {
  const AppContent({super.key});

  @override
  AppContentState createState() => AppContentState();
}

class AppContentState extends State<AppContent> with SingleTickerProviderStateMixin {
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

  Widget _buildTabButton(String text, int index, void Function(int index) selectTab) {
    final isActiveTab = _selectedIndex == index;

    return TextButton(
      style: ButtonStyle(
        backgroundColor: WidgetStatePropertyAll(
          isActiveTab ? context.themeColors.backgroundButtonPrimary : Colors.transparent,
        ),
      ),
      onPressed: () => selectTab(index),
      child: Text(
        text,
        style: isActiveTab ? context.textStyle.textNavActive : context.textStyle.textNav,
      ),
    );
  }

  void _applyPreset(preset) {
    // Применяем настройки пресета для конкретного пользователя
    if (preset.userType == UserType.driver) {
      // Получаем текущие настройки пассажира
      final currentState = context.read<ManualSettingsCubit>().state;

      // Применяем настройки пресета для водителя, сохраняя настройки пассажира
      context.read<ManualSettingsCubit>().applyPresetSettings(
            preset.settings, // настройки водителя из пресета
            currentState.passengerSettings, // текущие настройки пассажира
          );
    } else {
      // Получаем текущие настройки водителя
      final currentState = context.read<ManualSettingsCubit>().state;

      // Применяем настройки пресета для пассажира, сохраняя настройки водителя
      context.read<ManualSettingsCubit>().applyPresetSettings(
            currentState.driverSettings, // текущие настройки водителя
            preset.settings, // настройки пассажира из пресета
          );
    }

    // Обновляем информацию о последнем использовании пресета
    context.read<PresetCubit>().applyPreset(preset);

    // Показываем уведомление
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
            'Пресет "${preset.name}" применен для ${preset.userType == UserType.driver ? 'водителя' : 'пассажира'}'),
        backgroundColor: context.themeColors.primary,
      ),
    );
  }
}
