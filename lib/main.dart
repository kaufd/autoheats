// import 'package:android_automotive_plugin/android_automotive_plugin.dart';
import 'package:autoheat/src/cubit/mode_cubit.dart';
import 'package:autoheat/src/ui/themes/theme_cubit.dart';
import 'package:autoheat/src/ui/themes/theme_name.dart';
import 'package:autoheat/src/service_locator.dart';
import 'package:autoheat/src/ui/app_content.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  await setupServiceLocator();
  await locator<ThemeCubit>().initialize();

  // final AndroidAutomotivePlugin _androidAutomotivePlugin = AndroidAutomotivePlugin();
  // print("init plugin");

  // await _androidAutomotivePlugin.connect();
  // print("connected");

  runApp(const AutoheatApp());
}

class AutoheatApp extends StatelessWidget {
  const AutoheatApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MultiBlocProvider(
      providers: [
        BlocProvider<ThemeCubit>(
          create: (context) => locator<ThemeCubit>(),
        ),
        BlocProvider<ModeCubit>(
          create: (context) => locator<ModeCubit>(),
        ),
      ],
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
