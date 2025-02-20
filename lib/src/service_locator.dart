import 'package:autoheat/src/config/themes/theme_configurator.dart';
import 'package:autoheat/src/config/themes/theme_cubit.dart';
import 'package:autoheat/src/config/themes/theme_service.dart';
import 'package:get_it/get_it.dart';
import 'package:shared_preferences/shared_preferences.dart';

final locator = GetIt.instance;

Future<void> setupServiceLocator() async {
  final sharedPreferences = await SharedPreferences.getInstance();
  locator.registerSingleton<SharedPreferences>(sharedPreferences);

  locator.registerSingleton<ThemeService>(ThemeService(locator<SharedPreferences>()));

  locator.registerSingleton<ThemeConfigurator>(ThemeConfigurator());

  locator.registerSingleton<ThemeCubit>(
    ThemeCubit(locator<ThemeService>(), locator<ThemeConfigurator>()),
  );
}
