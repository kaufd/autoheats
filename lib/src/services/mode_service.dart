import 'package:autoheat/src/app_enums.dart';
import 'package:shared_preferences/shared_preferences.dart';

class ModeService {
  final SharedPreferences _prefs;

  ModeService(this._prefs);

  static const String _driverModeKey = 'driver_mode';
  static const String _driverHeatLevelKey = 'driver_heat_level';
  static const String _passengerModeKey = 'passenger_mode';
  static const String _passengerHeatLevelKey = 'passenger_heat_level';

  // Получить режим для пользователя
  HeatMode getMode(UserType userType) {
    final key = userType == UserType.driver ? _driverModeKey : _passengerModeKey;
    final modeString = _prefs.getString(key) ?? HeatMode.manual.name;
    return HeatModeExtension.fromString(modeString);
  }

  // Установить режим для пользователя
  Future<void> setMode(UserType userType, HeatMode mode) async {
    final key = userType == UserType.driver ? _driverModeKey : _passengerModeKey;
    await _prefs.setString(key, mode.name);
  }

  // Получить уровень нагрева для пользователя
  int getHeatLevel(UserType userType) {
    final key = userType == UserType.driver ? _driverHeatLevelKey : _passengerHeatLevelKey;
    return _prefs.getInt(key) ?? 0;
  }

  // Установить уровень нагрева для пользователя
  Future<void> setHeatLevel(UserType userType, int level) async {
    final key = userType == UserType.driver ? _driverHeatLevelKey : _passengerHeatLevelKey;
    await _prefs.setInt(key, level);
  }

  // Инициализировать значения по умолчанию
  Future<void> initializeDefaults() async {
    if (!_prefs.containsKey(_driverModeKey)) {
      await setMode(UserType.driver, HeatMode.manual);
    }
    if (!_prefs.containsKey(_passengerModeKey)) {
      await setMode(UserType.passenger, HeatMode.manual);
    }
    if (!_prefs.containsKey(_driverHeatLevelKey)) {
      await setHeatLevel(UserType.driver, 0);
    }
    if (!_prefs.containsKey(_passengerHeatLevelKey)) {
      await setHeatLevel(UserType.passenger, 0);
    }
  }

  // Получить все режимы
  Map<UserType, Map<String, dynamic>> getAllModes() {
    return {
      UserType.driver: {
        'mode': getMode(UserType.driver),
        'heatLevel': getHeatLevel(UserType.driver),
      },
      UserType.passenger: {
        'mode': getMode(UserType.passenger),
        'heatLevel': getHeatLevel(UserType.passenger),
      },
    };
  }
}
