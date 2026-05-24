// FILE: test/widget/app_smoke_test.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Widget smoke/regression тест корневого AutoheatApp.
//   SCOPE: MaterialApp shell, theme transition policy.
//   DEPENDS: M-MAIN, M-DI, M-THEME
//   LINKS: V-M-MAIN, M-MAIN
//   ROLE: TEST
//   MAP_MODE: LOCALS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   scenario-2 - AutoheatApp disables animated theme transitions
// END_MODULE_MAP

import 'package:autoheat/main.dart';
import 'package:autoheat/src/di/service_locator.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  // START_BLOCK_AUTOHEAT_APP_THEME_TRANSITION
  testWidgets('scenario-2: AutoheatApp switches themes without animation',
      (tester) async {
    tester.view.physicalSize = const Size(1920, 720);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    SharedPreferences.setMockInitialValues({});
    await locator.reset();
    await setupServiceLocator();
    addTearDown(locator.reset);

    await tester.pumpWidget(const AutoheatApp());

    final materialApp = tester.widget<MaterialApp>(find.byType(MaterialApp));

    expect(materialApp.themeAnimationDuration, Duration.zero);
  });
  // END_BLOCK_AUTOHEAT_APP_THEME_TRANSITION
}
