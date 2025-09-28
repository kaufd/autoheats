import 'package:shared_preferences/shared_preferences.dart';

class SettingsService {
  static const String _showCabinTemperatureKey = 'show_cabin_temperature';
  final SharedPreferences _prefs;

  SettingsService(this._prefs);

  bool getShowCabinTemperature() {
    return _prefs.getBool(_showCabinTemperatureKey) ?? true;
  }

  Future<void> setShowCabinTemperature(bool show) async {
    await _prefs.setBool(_showCabinTemperatureKey, show);
  }
}
