import 'package:shared_preferences/shared_preferences.dart';

import 'theme_name.dart';

class ThemeService {
  static const String _themeKey = 'selected_theme';
  final SharedPreferences _prefs;

  ThemeService(this._prefs);

  ThemeType? getSavedTheme() {
    final key = _prefs.getString(_themeKey);
    return key != null ? ThemeType.fromKey(key) : null;
  }

  Future<void> saveTheme(ThemeType theme) async {
    await _prefs.setString(_themeKey, theme.key);
  }

  String? get currentTheme => _prefs.getString(_themeKey);
}
