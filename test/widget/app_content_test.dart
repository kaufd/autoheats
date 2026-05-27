// FILE: test/widget/app_content_test.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Widget-регрессии AppContent для таб-навигации и user-aware redirect в PresetsTab.
//   SCOPE: Выбор вкладок, redirect из presets-сегмента HeatScreen в PresetsTab с корректным UserType.
//   DEPENDS: M-UI-APP, M-UI-HEAT, M-UI-PRESETS, M-DI, M-PRESET
//   LINKS: V-M-UI-APP, M-UI-APP, M-UI-PRESETS, FA-011
//   ROLE: TEST
//   MAP_MODE: LOCALS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   scenario-1 - redirect из passenger presets без passenger presets открывает PresetsTab на passenger
//   scenario-2 - redirect из driver presets без driver presets открывает PresetsTab на driver
// END_MODULE_MAP

import 'package:autoheat/main.dart';
import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/di/service_locator.dart';
import 'package:autoheat/src/models/manual_settings.dart';
import 'package:autoheat/src/presentation/screens/presets/presets_tab.dart';
import 'package:autoheat/src/services/preset_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  Future<void> pumpApp(
    WidgetTester tester, {
    bool driverHasPreset = false,
    bool passengerHasPreset = false,
  }) async {
    tester.view.physicalSize = const Size(1920, 720);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    SharedPreferences.setMockInitialValues({});
    await locator.reset();
    await setupServiceLocator();
    addTearDown(locator.reset);

    final presetService = locator<PresetService>();
    if (driverHasPreset) {
      await presetService.createPresetFromCurrentSettings(
        name: 'Driver preset',
        userType: UserType.driver,
        settings: ManualHeatSettings.defaultFor(UserType.driver),
      );
    }
    if (passengerHasPreset) {
      await presetService.createPresetFromCurrentSettings(
        name: 'Passenger preset',
        userType: UserType.passenger,
        settings: ManualHeatSettings.defaultFor(UserType.passenger),
      );
    }

    await tester.pumpWidget(const AutoheatApp());
    await tester.pumpAndSettle();
  }

  Finder presetsSegmentInHeatToggler(int togglerIndex) {
    return find.descendant(
      of: find.byType(SegmentedButton<String>).at(togglerIndex),
      matching: find.text('Пресеты'),
    );
  }

  SegmentedButton<UserType> userToggle(WidgetTester tester) {
    return tester.widget<SegmentedButton<UserType>>(
      find.byType(SegmentedButton<UserType>),
    );
  }

  // START_BLOCK_PRESETS_REDIRECT_SELECTED_USER
  testWidgets(
      'scenario-1: passenger presets redirect opens PresetsTab on passenger when passenger presets are absent',
      (tester) async {
    await pumpApp(
      tester,
      driverHasPreset: true,
      passengerHasPreset: false,
    );

    await tester.tap(presetsSegmentInHeatToggler(1));
    await tester.pumpAndSettle();

    expect(find.byType(PresetsTab), findsOneWidget);
    expect(userToggle(tester).selected, {UserType.passenger});
  });

  testWidgets(
      'scenario-2: driver presets redirect opens PresetsTab on driver when driver presets are absent',
      (tester) async {
    await pumpApp(
      tester,
      driverHasPreset: false,
      passengerHasPreset: true,
    );

    await tester.tap(presetsSegmentInHeatToggler(0));
    await tester.pumpAndSettle();

    expect(find.byType(PresetsTab), findsOneWidget);
    expect(userToggle(tester).selected, {UserType.driver});
  });
  // END_BLOCK_PRESETS_REDIRECT_SELECTED_USER
}
