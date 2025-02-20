import 'package:autoheat/src/config/themes/theme_configurator.dart';
import 'package:autoheat/src/config/themes/theme_manager.dart';
import 'package:autoheat/src/config/themes/theme_service.dart';
import 'package:get_it/get_it.dart';
import 'package:shared_preferences/shared_preferences.dart';

final locator = GetIt.instance;

Future<void> setupServiceLocator() async {
  // Регистрация асинхронных зависимостей
  final sharedPreferences = await SharedPreferences.getInstance();
  locator.registerSingleton<SharedPreferences>(sharedPreferences);

  // Регистрация сервисов
  locator.registerSingleton<ThemeService>(ThemeService(locator<SharedPreferences>()));

  // Регистрация других зависимостей
  locator.registerSingleton<ThemeConfigurator>(ThemeConfigurator());

  // Регистрация менеджеров
  locator.registerLazySingleton<ThemeManager>(
    () => ThemeManager(locator<ThemeService>(), ThemeConfigurator()),
  );
}
