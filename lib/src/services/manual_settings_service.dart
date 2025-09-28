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
        return _settingsFromJson(data);
      } catch (e) {
        return ManualHeatSettings.defaultFor(userType);
      }
    }

    return ManualHeatSettings.defaultFor(userType);
  }

  Future<void> saveSettings(ManualHeatSettings settings) async {
    final key = settings.userType == UserType.driver ? _driverSettingsKey : _passengerSettingsKey;
    final settingsJson = json.encode(_settingsToJson(settings));
    await _prefs.setString(key, settingsJson);
  }

  Map<String, dynamic> _settingsToJson(ManualHeatSettings settings) {
    return {
      'userType': settings.userType.name,
      'autoHeatLevels': settings.autoHeatLevels
          .map((level) => {
                'duration': level.duration,
                'level': level.level,
              })
          .toList(),
      'temperatureThreshold': settings.temperatureThreshold,
    };
  }

  ManualHeatSettings _settingsFromJson(Map<String, dynamic> json) {
    return ManualHeatSettings(
      userType: UserType.values.firstWhere(
        (type) => type.name == json['userType'],
        orElse: () => UserType.driver,
      ),
      autoHeatLevels: (json['autoHeatLevels'] as List)
          .map((levelJson) => AutoHeatLevel(
                duration: levelJson['duration'],
                level: levelJson['level'],
              ))
          .toList(),
      temperatureThreshold: (json['temperatureThreshold'] as num).toDouble(),
    );
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
