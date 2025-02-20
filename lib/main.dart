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

  runApp(const AutoheatApp());
}

class AutoheatApp extends StatelessWidget {
  const AutoheatApp({super.key});

  @override
  Widget build(BuildContext context) {
    return BlocProvider(
      create: (_) => locator<ThemeCubit>(),
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
