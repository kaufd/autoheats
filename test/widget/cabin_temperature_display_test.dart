// FILE: test/widget/cabin_temperature_display_test.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Widget-тест CabinTemperatureDisplay — UI читает CabinTemperatureCubit.
//   SCOPE: initial rendered temperature, update after HVAC event, SettingsCubit visibility gate.
//   DEPENDS: M-UI-HEAT, M-CABIN-TEMPERATURE, M-SETTINGS, M-HVAC
//   LINKS: V-M-UI-HEAT, V-M-CABIN-TEMPERATURE, FA-003
//   ROLE: TEST
//   MAP_MODE: LOCALS
// END_MODULE_CONTRACT

import 'package:autoheat/src/cubit/cabin_temperature_cubit.dart';
import 'package:autoheat/src/cubit/settings_cubit.dart';
import 'package:autoheat/src/presentation/screens/heat/components/cabin_temperature_display.dart';
import 'package:autoheat/src/services/settings_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../_helpers/fake_hvac_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  // START_BLOCK_DISPLAY_UPDATES
  testWidgets(
      'scenario-1: display renders initial temperature and HVAC updates',
      (tester) async {
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final fakeHvac = FakeHvacService()..programmedTemperature = 18.5;
    final settingsCubit = SettingsCubit(SettingsService(prefs));
    final temperatureCubit = CabinTemperatureCubit(fakeHvac);
    addTearDown(settingsCubit.close);
    addTearDown(temperatureCubit.close);
    await tester.pumpWidget(
      MultiBlocProvider(
        providers: [
          BlocProvider<SettingsCubit>.value(value: settingsCubit),
          BlocProvider<CabinTemperatureCubit>.value(value: temperatureCubit),
        ],
        child: const MaterialApp(
          home: Scaffold(body: CabinTemperatureDisplay()),
        ),
      ),
    );
    await tester.pump();

    expect(find.text('18.5 °C'), findsOneWidget);

    fakeHvac.emitTemperature(-3.0);
    expect(temperatureCubit.state.celsius, -3.0);
    await tester.pump();
    await tester.pump();

    expect(find.text('-3.0 °C'), findsOneWidget);
  });
  // END_BLOCK_DISPLAY_UPDATES
}
