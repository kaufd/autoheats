// FILE: test/unit/service_locator_test.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Unit-тесты GetIt bootstrap для M-DI.
//   SCOPE: complete registration set and idempotent repeated setupServiceLocator calls.
//   DEPENDS: M-DI, M-HVAC, M-MODE, M-CABIN-TEMPERATURE, M-SETTINGS, M-MANUAL-SETTINGS, M-PRESET, M-THEME
//   LINKS: V-M-DI, FA-008
//   ROLE: TEST
//   MAP_MODE: LOCALS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   scenario-1 - setupServiceLocator registers core services/cubits
//   scenario-2 - repeated setupServiceLocator is idempotent in one isolate
// END_MODULE_MAP

import 'package:autoheat/src/cubit/cabin_temperature_cubit.dart';
import 'package:autoheat/src/cubit/manual_settings_cubit.dart';
import 'package:autoheat/src/cubit/mode_cubit.dart';
import 'package:autoheat/src/cubit/preset_cubit.dart';
import 'package:autoheat/src/cubit/settings_cubit.dart';
import 'package:autoheat/src/di/service_locator.dart';
import 'package:autoheat/src/presentation/themes/theme_configurator.dart';
import 'package:autoheat/src/presentation/themes/theme_cubit.dart';
import 'package:autoheat/src/presentation/themes/theme_service.dart';
import 'package:autoheat/src/services/hvac_service.dart';
import 'package:autoheat/src/services/manual_settings_service.dart';
import 'package:autoheat/src/services/mode_service.dart';
import 'package:autoheat/src/services/preset_service.dart';
import 'package:autoheat/src/services/settings_service.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    await locator.reset();
  });

  tearDown(() async {
    if (locator.isRegistered<HvacService>()) {
      locator<HvacService>().dispose();
    }
    await locator.reset();
  });

  // START_BLOCK_DI_REGISTRATION
  test('scenario-1: setupServiceLocator registers core dependencies', () async {
    await setupServiceLocator();

    expect(locator.isRegistered<SharedPreferences>(), isTrue);
    expect(locator.isRegistered<HvacService>(), isTrue);
    expect(locator.isRegistered<ThemeService>(), isTrue);
    expect(locator.isRegistered<SettingsService>(), isTrue);
    expect(locator.isRegistered<ManualSettingsService>(), isTrue);
    expect(locator.isRegistered<PresetService>(), isTrue);
    expect(locator.isRegistered<ModeService>(), isTrue);
    expect(locator.isRegistered<ThemeConfigurator>(), isTrue);
    expect(locator.isRegistered<ThemeCubit>(), isTrue);
    expect(locator.isRegistered<ModeCubit>(), isTrue);
    expect(locator.isRegistered<CabinTemperatureCubit>(), isTrue);
    expect(locator.isRegistered<SettingsCubit>(), isTrue);
    expect(locator.isRegistered<ManualSettingsCubit>(), isTrue);
    expect(locator.isRegistered<PresetCubit>(), isTrue);
  });

  test('scenario-2: repeated setupServiceLocator is idempotent', () async {
    await setupServiceLocator();
    final firstHvac = locator<HvacService>();
    final firstModeCubit = locator<ModeCubit>();

    await setupServiceLocator();

    expect(locator<HvacService>(), same(firstHvac));
    expect(locator<ModeCubit>(), same(firstModeCubit));
    expect(locator.isRegistered<PresetCubit>(), isTrue);
  });
  // END_BLOCK_DI_REGISTRATION
}
