import 'package:autoheat/src/config/themes/theme_configurator.dart';
import 'package:autoheat/src/config/themes/theme_manager.dart';
import 'package:autoheat/src/config/themes/theme_name.dart';
import 'package:autoheat/src/config/themes/theme_service.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  final sharedPrefs = await SharedPreferences.getInstance();
  final themeService = ThemeService(sharedPrefs);
  final themeManager = ThemeManager(themeService, ThemeConfigurator());

  await themeManager.initialize();

  runApp(
    ChangeNotifierProvider(
      create: (_) => themeManager,
      child: const AutoheatApp(),
    ),
  );
  // runApp(AutoheatApp());
}

class AutoheatApp extends StatelessWidget {
  const AutoheatApp({super.key});

  @override
  Widget build(BuildContext context) {
    return Consumer<ThemeManager>(
      builder: (context, themeManager, child) {
        return MaterialApp(
          theme: themeManager.getCurrentTheme(context),
          home: HomeScreen(),
        );
      },
    );
  }
}

class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final themeManager = Provider.of<ThemeManager>(context);

    return Scaffold(
      appBar: AppBar(title: Text('Multiple Themes Example')),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: <Widget>[
            ElevatedButton(
              onPressed: () => themeManager.changeTheme(ThemeName.base),
              child: Text('Green Theme'),
            ),
            ElevatedButton(
              onPressed: () => themeManager.changeTheme(ThemeName.red),
              child: Text('Red Theme'),
            ),
            ElevatedButton(
              onPressed: () => themeManager.changeTheme(ThemeName.white),
              child: Text('White Theme'),
            ),
          ],
        ),
      ),
    );
  }
}
