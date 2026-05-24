// FILE: test/widget/settings_screen_test.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Widget smoke тест SettingsScreen на head-unit-like constraints.
//   SCOPE: build/layout без RenderFlex unbounded-height ошибок.
//   DEPENDS: M-UI-SETTINGS, M-MANUAL-SETTINGS, M-SETTINGS, M-THEME
//   LINKS: V-M-UI-SETTINGS, FA-009
//   ROLE: TEST
//   MAP_MODE: LOCALS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   scenario-3 - SettingsScreen builds at 1024x600 without layout exception
// END_MODULE_MAP

import 'package:autoheat/src/config/app_theme.dart';
import 'package:autoheat/src/cubit/manual_settings_cubit.dart';
import 'package:autoheat/src/cubit/settings_cubit.dart';
import 'package:autoheat/src/presentation/screens/settings/settings_screen.dart';
import 'package:autoheat/src/presentation/themes/theme_configurator.dart';
import 'package:autoheat/src/presentation/themes/theme_cubit.dart';
import 'package:autoheat/src/presentation/themes/theme_service.dart';
import 'package:autoheat/src/services/manual_settings_service.dart';
import 'package:autoheat/src/services/settings_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  // START_BLOCK_HEAD_UNIT_SMOKE
  testWidgets('scenario-3: SettingsScreen builds at head-unit size',
      (tester) async {
    tester.view.physicalSize = const Size(1024, 600);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final settingsCubit = SettingsCubit(SettingsService(prefs));
    final manualSettingsCubit =
        ManualSettingsCubit(ManualSettingsService(prefs));
    final themeCubit = ThemeCubit(ThemeService(prefs), ThemeConfigurator());
    addTearDown(settingsCubit.close);
    addTearDown(manualSettingsCubit.close);
    addTearDown(themeCubit.close);

    await tester.pumpWidget(
      MultiBlocProvider(
        providers: [
          BlocProvider<SettingsCubit>.value(value: settingsCubit),
          BlocProvider<ManualSettingsCubit>.value(value: manualSettingsCubit),
          BlocProvider<ThemeCubit>.value(value: themeCubit),
        ],
        child: Builder(
          builder: (context) {
            return MaterialApp(
              theme: AppTheme.base(context),
              home: const Scaffold(body: SettingsScreen()),
            );
          },
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
    expect(find.byType(SettingsScreen), findsOneWidget);
  });
  // END_BLOCK_HEAD_UNIT_SMOKE
}
