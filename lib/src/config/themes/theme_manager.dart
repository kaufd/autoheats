import 'package:flutter/material.dart';
import 'theme_name.dart';
import 'theme_service.dart';
import 'theme_configurator.dart';

class ThemeManager extends ChangeNotifier {
  final ThemeService _themeService;
  final ThemeConfigurator _configurator;

  ThemeName _currentTheme = ThemeName.base;

  ThemeManager(this._themeService, this._configurator);

  Future<void> initialize() async {
    _currentTheme = await _loadSavedTheme();
    notifyListeners();
  }

  Future<ThemeName> _loadSavedTheme() async {
    final savedTheme = _themeService.getSavedTheme();
    return savedTheme ?? ThemeName.base;
  }

  ThemeData getCurrentTheme(BuildContext context) {
    return _configurator.configureTheme(
      themeName: _currentTheme,
      context: context,
    );
  }

  Future<void> changeTheme(ThemeName themeName) async {
    if (_currentTheme == themeName) return;

    _currentTheme = themeName;
    await _themeService.saveTheme(themeName);
    notifyListeners();
    print('Theme changed to $_currentTheme');
  }

  ThemeName get currentThemeName => _currentTheme;
}
