import 'package:autoheat/src/config/themes/theme_manager.dart';
import 'package:autoheat/src/config/themes/theme_name.dart';
import 'package:autoheat/src/service_locator.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  await setupServiceLocator();
  await locator<ThemeManager>().initialize();

  // runApp(const AutoheatApp());
  runApp(
    ChangeNotifierProvider(
      create: (_) => locator<ThemeManager>(),
      child: const AutoheatApp(),
    ),
  );
}

class AutoheatApp extends StatelessWidget {
  const AutoheatApp({super.key});

  @override
  Widget build(BuildContext context) {
    print('Rebuilding MaterialApp');
    return ChangeNotifierProvider(
      create: (_) => locator<ThemeManager>(),
      child: Builder(
        builder: (context) {
          return MaterialApp(
            theme: context.watch<ThemeManager>().getCurrentTheme(context),
            home: const HomeScreen(),
          );
        },
      ),
    );
  }
}

class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final themeManager = locator<ThemeManager>();

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
