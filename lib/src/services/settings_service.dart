// FILE: lib/src/services/settings_service.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Persist global application settings used by the settings UI and debug mode.
//   SCOPE: cabin-temperature visibility and debug-mode SharedPreferences keys.
//   DEPENDS: M-SETTINGS
//   LINKS: M-SETTINGS, V-M-SETTINGS
//   MAP_MODE: SUMMARY
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   SettingsService - persistence facade for global settings
//   getShowCabinTemperature/setShowCabinTemperature - cabin-temperature visibility
//   getDebugMode/setDebugMode - diagnostic mode preference
// END_MODULE_MAP

import 'package:shared_preferences/shared_preferences.dart';

class SettingsService {
  static const String _showCabinTemperatureKey = 'show_cabin_temperature';
  static const String _debugModeKey = 'debug_mode';
  final SharedPreferences _prefs;

  SettingsService(this._prefs);

  bool getShowCabinTemperature() {
    return _prefs.getBool(_showCabinTemperatureKey) ?? true;
  }

  Future<void> setShowCabinTemperature(bool show) async {
    await _prefs.setBool(_showCabinTemperatureKey, show);
  }

  bool getDebugMode() {
    return _prefs.getBool(_debugModeKey) ?? false;
  }

  Future<void> setDebugMode(bool enabled) async {
    await _prefs.setBool(_debugModeKey, enabled);
  }
}
