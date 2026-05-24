// FILE: test/widget/white_theme_button_contrast_test.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Widget regression тесты читаемости primary-кнопок в белой теме.
//   SCOPE: CustomAlertDialog confirm, PresetsSettings save, ThemeSelector selected button.
//   DEPENDS: M-UI-SETTINGS, M-THEME
//   LINKS: V-M-UI-SETTINGS, V-M-THEME, FA-009
//   ROLE: TEST
//   MAP_MODE: LOCALS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   scenario-4 - белая тема использует black foreground на selected/primary кнопках
// END_MODULE_MAP

import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/config/app_theme.dart';
import 'package:autoheat/src/config/color_constants.dart';
import 'package:autoheat/src/cubit/manual_settings_cubit.dart';
import 'package:autoheat/src/models/manual_settings.dart';
import 'package:autoheat/src/presentation/screens/settings/components/presets_settings.dart';
import 'package:autoheat/src/presentation/screens/settings/components/theme_selector.dart';
import 'package:autoheat/src/presentation/themes/theme_configurator.dart';
import 'package:autoheat/src/presentation/themes/theme_cubit.dart';
import 'package:autoheat/src/presentation/themes/theme_name.dart';
import 'package:autoheat/src/presentation/themes/theme_service.dart';
import 'package:autoheat/src/presentation/ui/custom_alert_dialog.dart';
import 'package:autoheat/src/services/manual_settings_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  // START_BLOCK_WHITE_THEME_BUTTON_CONTRAST
  testWidgets('scenario-4: dialog confirm button is readable in white theme',
      (tester) async {
    await tester.pumpWidget(
      Builder(
        builder: (context) => MaterialApp(
          theme: AppTheme.white(context),
          home: const CustomAlertDialog(
            content: SizedBox.shrink(),
            confirmText: 'Сохранить',
          ),
        ),
      ),
    );

    final button = _textButtonByText(tester, 'Сохранить');

    expect(_foregroundColor(button), ColorConstants.systemBlack);
  });

  testWidgets('scenario-4: preset save button is readable in white theme',
      (tester) async {
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final manualSettingsCubit =
        ManualSettingsCubit(ManualSettingsService(prefs));
    addTearDown(manualSettingsCubit.close);

    final settingsState = ManualSettingsState(
      driverSettings: ManualHeatSettings.defaultFor(UserType.driver),
      passengerSettings: ManualHeatSettings.defaultFor(UserType.passenger),
    );

    await tester.pumpWidget(
      BlocProvider<ManualSettingsCubit>.value(
        value: manualSettingsCubit,
        child: Builder(
          builder: (context) => MaterialApp(
            theme: AppTheme.white(context),
            home: Scaffold(
              body: PresetsSettings(
                settingsState: settingsState,
                onAutoHeatLevelChanged: (_, __, ___) {},
                onTemperatureThresholdChanged: (_, __) {},
              ),
            ),
          ),
        ),
      ),
    );

    final button = _textButtonByText(tester, 'Сохранить');

    expect(_foregroundColor(button), ColorConstants.systemBlack);
  });

  testWidgets('scenario-4: selected white theme button is readable',
      (tester) async {
    SharedPreferences.setMockInitialValues(
        {'selected_theme': ThemeType.white.key});
    final prefs = await SharedPreferences.getInstance();
    final themeCubit = ThemeCubit(ThemeService(prefs), ThemeConfigurator());
    await themeCubit.initialize();
    addTearDown(themeCubit.close);

    await tester.pumpWidget(
      BlocProvider<ThemeCubit>.value(
        value: themeCubit,
        child: Builder(
          builder: (context) => MaterialApp(
            theme: AppTheme.white(context),
            home: const Scaffold(body: ThemeSelector()),
          ),
        ),
      ),
    );

    final button = _textButtonByText(tester, 'Белая');

    expect(_foregroundColor(button), ColorConstants.systemBlack);
  });
  // END_BLOCK_WHITE_THEME_BUTTON_CONTRAST
}

TextButton _textButtonByText(WidgetTester tester, String text) {
  final buttonFinder = find.ancestor(
    of: find.text(text).first,
    matching: find.byType(TextButton),
  );
  return tester.widget<TextButton>(buttonFinder.first);
}

Color? _foregroundColor(TextButton button) {
  return button.style?.foregroundColor?.resolve(<WidgetState>{});
}
