// FILE: lib/src/di/service_locator.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Регистрация SharedPreferences и всех сервисов/кубитов в GetIt —
//            единый bootstrap DI для UI- и background-изолята.
//   SCOPE: setupServiceLocator (async), глобальный locator.
//   DEPENDS: M-HVAC, M-MODE, M-PRESET, M-SETTINGS, M-MANUAL-SETTINGS, M-THEME, M-AUTO-HEAT, M-CABIN-TEMPERATURE
//   LINKS: M-DI, V-M-DI, DF-BACKGROUND, DF-INIT-TEMP, FA-008
//   ROLE: RUNTIME
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   locator - GetIt.instance, глобальный контейнер зависимостей
//   setupServiceLocator - регистрирует SharedPreferences, сервисы и кубиты;
//                         идемпотентен в рамках isolate; вызывается И в UI-, И в background-изоляте
//   _registerSingletonIfAbsent - lazy guard вокруг GetIt.registerSingleton
//   _registerValueIfAbsent - guard для уже созданного async SharedPreferences
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.3.0 - Phase-4 Slice-8: setupServiceLocator is idempotent per isolate]
//   PREVIOUS_CHANGE: [v1.2.0 - Phase-4 Slice-3: регистрируется CabinTemperatureCubit]
// END_CHANGE_SUMMARY

import 'package:autoheat/src/cubit/cabin_temperature_cubit.dart';
import 'package:autoheat/src/cubit/mode_cubit.dart';
import 'package:autoheat/src/cubit/settings_cubit.dart';
import 'package:autoheat/src/cubit/manual_settings_cubit.dart';
import 'package:autoheat/src/cubit/preset_cubit.dart';
import 'package:autoheat/src/presentation/themes/theme_configurator.dart';
import 'package:autoheat/src/presentation/themes/theme_cubit.dart';
import 'package:autoheat/src/presentation/themes/theme_service.dart';
import 'package:autoheat/src/services/mode_service.dart';
import 'package:autoheat/src/services/settings_service.dart';
import 'package:autoheat/src/services/manual_settings_service.dart';
import 'package:autoheat/src/services/preset_service.dart';
import 'package:autoheat/src/services/hvac_service.dart';
import 'package:get_it/get_it.dart';
import 'package:shared_preferences/shared_preferences.dart';

final locator = GetIt.instance;

Future<void> setupServiceLocator() async {
  final sharedPreferences = await SharedPreferences.getInstance();
  _registerValueIfAbsent<SharedPreferences>(sharedPreferences);

  _registerSingletonIfAbsent<HvacService>(() => HvacService());

  _registerSingletonIfAbsent<ThemeService>(
      () => ThemeService(locator<SharedPreferences>()));

  _registerSingletonIfAbsent<SettingsService>(
      () => SettingsService(locator<SharedPreferences>()));

  _registerSingletonIfAbsent<ManualSettingsService>(
      () => ManualSettingsService(locator<SharedPreferences>()));

  _registerSingletonIfAbsent<PresetService>(
      () => PresetService(locator<SharedPreferences>()));

  _registerSingletonIfAbsent<ModeService>(
      () => ModeService(locator<SharedPreferences>()));

  _registerSingletonIfAbsent<ThemeConfigurator>(() => ThemeConfigurator());

  _registerSingletonIfAbsent<ThemeCubit>(
    () => ThemeCubit(locator<ThemeService>(), locator<ThemeConfigurator>()),
  );

  _registerSingletonIfAbsent<ModeCubit>(
    () => ModeCubit(
      locator<ModeService>(),
      locator<HvacService>(),
      locator<PresetService>(),
    ),
  );

  _registerSingletonIfAbsent<CabinTemperatureCubit>(
    () => CabinTemperatureCubit(locator<HvacService>()),
  );

  _registerSingletonIfAbsent<SettingsCubit>(
      () => SettingsCubit(locator<SettingsService>()));

  _registerSingletonIfAbsent<ManualSettingsCubit>(
      () => ManualSettingsCubit(locator<ManualSettingsService>()));

  _registerSingletonIfAbsent<PresetCubit>(
      () => PresetCubit(locator<PresetService>()));
}

void _registerValueIfAbsent<T extends Object>(T instance) {
  if (locator.isRegistered<T>()) return;
  locator.registerSingleton<T>(instance);
}

void _registerSingletonIfAbsent<T extends Object>(T Function() create) {
  if (locator.isRegistered<T>()) return;
  locator.registerSingleton<T>(create());
}
