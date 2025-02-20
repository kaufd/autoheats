import 'package:shared_preferences/shared_preferences.dart';

import 'theme_name.dart';

class ThemeService {
  static const String _themeKey = 'selected_theme';
  final SharedPreferences _prefs;

  ThemeService(this._prefs);

  ThemeName? getSavedTheme() {
    final key = _prefs.getString(_themeKey);
    return key != null ? ThemeName.fromKey(key) : null;
  }

  Future<void> saveTheme(ThemeName theme) async {
    await _prefs.setString(_themeKey, theme.key);
  }
}
