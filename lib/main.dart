import 'package:autoheat/src/cubit/settings_cubit.dart';
import 'package:autoheat/src/di/app_bloc_providers.dart';
import 'package:autoheat/src/presentation/themes/theme_cubit.dart';
import 'package:autoheat/src/presentation/themes/theme_name.dart';
import 'package:autoheat/src/di/service_locator.dart';
import 'package:autoheat/src/presentation/app_content.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  await setupServiceLocator();
  await locator<ThemeCubit>().initialize();
  await locator<SettingsCubit>().initialize();

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
            home: const AppContent(),
          );
        },
      ),
    );
  }
}
