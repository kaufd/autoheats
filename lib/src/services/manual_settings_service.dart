import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/models/manual_settings.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'dart:convert';

class ManualSettingsService {
  final SharedPreferences _prefs;

  ManualSettingsService(this._prefs);

  static const String _driverSettingsKey = 'manual_settings_driver';
  static const String _passengerSettingsKey = 'manual_settings_passenger';

  Future<ManualHeatSettings> getSettings(UserType userType) async {
    final key = userType == UserType.driver ? _driverSettingsKey : _passengerSettingsKey;
    final settingsJson = _prefs.getString(key);

    if (settingsJson != null) {
      try {
        final Map<String, dynamic> data = json.decode(settingsJson);
        return ManualHeatSettings.fromJson(data);
      } catch (e) {
        return ManualHeatSettings.defaultFor(userType);
      }
    }

    return ManualHeatSettings.defaultFor(userType);
  }

  Future<void> saveSettings(ManualHeatSettings settings, UserType userType) async {
    final key = userType == UserType.driver ? _driverSettingsKey : _passengerSettingsKey;
    final settingsJson = json.encode(settings.toJson());
    await _prefs.setString(key, settingsJson);
  }

  Future<void> clearSettings(UserType userType) async {
    final key = userType == UserType.driver ? _driverSettingsKey : _passengerSettingsKey;
    await _prefs.remove(key);
  }

  Future<void> clearAllSettings() async {
    await _prefs.remove(_driverSettingsKey);
    await _prefs.remove(_passengerSettingsKey);
  }
}
