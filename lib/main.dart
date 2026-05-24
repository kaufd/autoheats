// FILE: lib/main.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Flutter entrypoint and root app shell bootstrap.
//   SCOPE: setupServiceLocator, initial cubit/bootstrap services, AutoheatApp MaterialApp.
//   DEPENDS: M-DI, M-BLOC-PROVIDERS, M-THEME, M-SETTINGS, M-ACCESSIBILITY, M-BACKGROUND, M-UI-APP
//   LINKS: M-MAIN, V-M-MAIN, DF-BACKGROUND
//   ROLE: RUNTIME
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   main - UI isolate bootstrap and runApp(AutoheatApp)
//   AutoheatApp - root MultiBlocProvider + MaterialApp with ThemeCubit-driven theme
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.1.0 - Disable MaterialApp theme animation to avoid old-theme flicker]
// END_CHANGE_SUMMARY

import 'package:autoheat/src/cubit/settings_cubit.dart';
import 'package:autoheat/src/di/app_bloc_providers.dart';
import 'package:autoheat/src/presentation/themes/theme_cubit.dart';
import 'package:autoheat/src/presentation/themes/theme_name.dart';
import 'package:autoheat/src/di/service_locator.dart';
import 'package:autoheat/src/presentation/app_content.dart';
import 'package:autoheat/src/services/accessibility_service.dart';
import 'package:autoheat/src/services/background_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  await setupServiceLocator();
  await locator<ThemeCubit>().initialize();
  await locator<SettingsCubit>().initialize();

  await initializeAccessibilityService();
  await initializeBackgroundService();

  runApp(const AutoheatApp());
}

class AutoheatApp extends StatelessWidget {
  const AutoheatApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MultiBlocProvider(
      providers: BlockProviders.initiateBlocs(),
      child: BlocBuilder<ThemeCubit, ThemeType>(
        builder: (context, theme) {
          final themeCubit = context.read<ThemeCubit>();

          return MaterialApp(
            title: 'AutoHeat',
            theme: themeCubit.getCurrentTheme(context),
            themeAnimationDuration: Duration.zero,
            home: const AppContent(),
          );
        },
      ),
    );
  }
}
