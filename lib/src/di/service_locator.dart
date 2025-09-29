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
import 'package:autoheat/src/services/seat_heat_service.dart';
import 'package:autoheat/src/services/temperature_sensor_service.dart';
import 'package:autoheat/src/services/temperature_event_service.dart';
import 'package:android_automotive_plugin/android_automotive_plugin.dart';
import 'package:android_automotive_plugin/car/hvac_manager.dart';
import 'package:get_it/get_it.dart';
import 'package:shared_preferences/shared_preferences.dart';

final locator = GetIt.instance;

Future<void> setupServiceLocator() async {
  final sharedPreferences = await SharedPreferences.getInstance();
  locator.registerSingleton<SharedPreferences>(sharedPreferences);

  final androidAutomotivePlugin = AndroidAutomotivePlugin();
  try {
    await androidAutomotivePlugin.connect();
    print("AutoHeat: AndroidAutomotivePlugin успешно подключен");
    locator.registerSingleton<AndroidAutomotivePlugin>(androidAutomotivePlugin);
  } catch (e) {
    print("AutoHeat: Ошибка подключения AndroidAutomotivePlugin: $e");
    print("AutoHeat: Приложение будет работать в режиме симуляции");
    locator.registerSingleton<AndroidAutomotivePlugin>(androidAutomotivePlugin);
  }

  locator.registerSingleton<CarHvacManager>(CarHvacManager(androidAutomotivePlugin));

  locator.registerSingleton<SeatHeatService>(SeatHeatService(locator<CarHvacManager>()));

  locator.registerSingleton<TemperatureSensorService>(
      TemperatureSensorService(locator<CarHvacManager>()));

  locator.registerSingleton<TemperatureEventService>(
      TemperatureEventService(locator<AndroidAutomotivePlugin>()));

  locator.registerSingleton<ThemeService>(ThemeService(locator<SharedPreferences>()));

  locator.registerSingleton<SettingsService>(SettingsService(locator<SharedPreferences>()));

  locator.registerSingleton<ManualSettingsService>(
      ManualSettingsService(locator<SharedPreferences>()));

  locator.registerSingleton<PresetService>(PresetService(locator<SharedPreferences>()));

  locator.registerSingleton<ModeService>(ModeService(locator<SharedPreferences>()));

  locator.registerSingleton<ThemeConfigurator>(ThemeConfigurator());

  locator.registerSingleton<ThemeCubit>(
    ThemeCubit(locator<ThemeService>(), locator<ThemeConfigurator>()),
  );

  locator.registerSingleton<ModeCubit>(ModeCubit(locator<ModeService>(), locator<SeatHeatService>(),
      locator<TemperatureSensorService>(), locator<TemperatureEventService>()));

  locator.registerSingleton<SettingsCubit>(SettingsCubit(locator<SettingsService>()));

  locator.registerSingleton<ManualSettingsCubit>(
      ManualSettingsCubit(locator<ManualSettingsService>()));

  locator.registerSingleton<PresetCubit>(PresetCubit(locator<PresetService>()));
}
