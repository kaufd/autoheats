import 'package:autoheat/src/cubit/mode_cubit.dart';
import 'package:autoheat/src/cubit/settings_cubit.dart';
import 'package:autoheat/src/cubit/manual_settings_cubit.dart';
import 'package:autoheat/src/models/mode.dart';
import 'package:autoheat/src/presentation/themes/theme_configurator.dart';
import 'package:autoheat/src/presentation/themes/theme_cubit.dart';
import 'package:autoheat/src/presentation/themes/theme_service.dart';
import 'package:autoheat/src/repository/mode/repository.dart';
import 'package:autoheat/src/services/settings_service.dart';
import 'package:autoheat/src/services/manual_settings_service.dart';
import 'package:get_it/get_it.dart';
import 'package:realm/realm.dart';
import 'package:shared_preferences/shared_preferences.dart';

final locator = GetIt.instance;

Future<void> setupServiceLocator() async {
  final sharedPreferences = await SharedPreferences.getInstance();
  locator.registerSingleton<SharedPreferences>(sharedPreferences);

  locator.registerSingleton<Realm>(Realm(Configuration.local([Mode.schema])));

  locator.registerSingleton<ThemeService>(ThemeService(locator<SharedPreferences>()));

  locator.registerSingleton<SettingsService>(SettingsService(locator<SharedPreferences>()));

  locator.registerSingleton<ManualSettingsService>(
      ManualSettingsService(locator<SharedPreferences>()));

  locator.registerSingleton<ThemeConfigurator>(ThemeConfigurator());

  locator.registerSingleton<ThemeCubit>(
    ThemeCubit(locator<ThemeService>(), locator<ThemeConfigurator>()),
  );

  locator.registerSingleton<ModeRepository>(ModeRepository(locator<Realm>()));

  locator.registerSingleton<ModeCubit>(ModeCubit(locator<ModeRepository>()));

  locator.registerSingleton<SettingsCubit>(SettingsCubit(locator<SettingsService>()));

  locator.registerSingleton<ManualSettingsCubit>(
      ManualSettingsCubit(locator<ManualSettingsService>()));
}
