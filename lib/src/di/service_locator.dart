// FILE: lib/src/di/service_locator.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Регистрация SharedPreferences и всех сервисов/кубитов в GetIt —
//            единый bootstrap DI для UI- и background-изолята.
//   SCOPE: setupServiceLocator (async), глобальный locator.
//   DEPENDS: M-HVAC, M-MODE, M-PRESET, M-SETTINGS, M-MANUAL-SETTINGS, M-THEME, M-AUTO-HEAT, M-CABIN-TEMPERATURE
//   LINKS: M-DI, V-M-DI, DF-BACKGROUND, DF-INIT-TEMP
//   ROLE: RUNTIME
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   locator - GetIt.instance, глобальный контейнер зависимостей
//   setupServiceLocator - регистрирует SharedPreferences, сервисы и кубиты;
//                         вызывается И в UI-, И в background-изоляте
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.2.0 - Phase-4 Slice-3: регистрируется CabinTemperatureCubit]
//   PREVIOUS_CHANGE: [v1.1.0 - Phase-4 Slice-2: ModeCubit получает ManualSettingsService]
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
  locator.registerSingleton<SharedPreferences>(sharedPreferences);

  locator.registerSingleton<HvacService>(HvacService());

  locator.registerSingleton<ThemeService>(
      ThemeService(locator<SharedPreferences>()));

  locator.registerSingleton<SettingsService>(
      SettingsService(locator<SharedPreferences>()));

  locator.registerSingleton<ManualSettingsService>(
      ManualSettingsService(locator<SharedPreferences>()));

  locator.registerSingleton<PresetService>(
      PresetService(locator<SharedPreferences>()));

  locator.registerSingleton<ModeService>(
      ModeService(locator<SharedPreferences>()));

  locator.registerSingleton<ThemeConfigurator>(ThemeConfigurator());

  locator.registerSingleton<ThemeCubit>(
    ThemeCubit(locator<ThemeService>(), locator<ThemeConfigurator>()),
  );

  locator.registerSingleton<ModeCubit>(
    ModeCubit(
      locator<ModeService>(),
      locator<HvacService>(),
      locator<ManualSettingsService>(),
    ),
  );

  locator.registerSingleton<CabinTemperatureCubit>(
    CabinTemperatureCubit(locator<HvacService>()),
  );

  locator.registerSingleton<SettingsCubit>(
      SettingsCubit(locator<SettingsService>()));

  locator.registerSingleton<ManualSettingsCubit>(
      ManualSettingsCubit(locator<ManualSettingsService>()));

  locator.registerSingleton<PresetCubit>(PresetCubit(locator<PresetService>()));
}
