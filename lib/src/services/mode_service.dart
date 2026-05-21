// FILE: lib/src/services/mode_service.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Persistence режима (manual/presets/auto) и уровня подогрева для
//            driver/passenger в SharedPreferences — слой данных модуля M-MODE.
//   SCOPE: чтение/запись режима и уровня по стабильным ключам, засев дефолтов.
//   DEPENDS: M-ENUMS
//   LINKS: M-MODE, V-M-MODE
//   ROLE: RUNTIME
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   ModeService - CRUD режимов и уровней через SharedPreferences
//   ModeService(SharedPreferences) - конструктор с инъекцией prefs
//   _driverModeKey / _passengerModeKey - ключи режима (стабильный контракт)
//   _driverHeatLevelKey / _passengerHeatLevelKey - ключи уровня (стабильный контракт)
//   getMode(UserType) - чтение HeatMode; дефолт manual
//   setMode(UserType, HeatMode) - запись HeatMode по .name
//   getHeatLevel(UserType) - чтение уровня; дефолт 0
//   setHeatLevel(UserType, int) - запись уровня
//   initializeDefaults - засев отсутствующих ключей дефолтами
//   getAllModes - снимок режим+уровень для обоих UserType
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v0.2.0 - GRACE-инициализация: добавлены MODULE_CONTRACT и MODULE_MAP]
// END_CHANGE_SUMMARY

import 'package:autoheat/src/app_enums.dart';
import 'package:shared_preferences/shared_preferences.dart';

class ModeService {
  final SharedPreferences _prefs;

  ModeService(this._prefs);

  static const String _driverModeKey = 'driver_mode';
  static const String _driverHeatLevelKey = 'driver_heat_level';
  static const String _passengerModeKey = 'passenger_mode';
  static const String _passengerHeatLevelKey = 'passenger_heat_level';

  HeatMode getMode(UserType userType) {
    final key = userType == UserType.driver ? _driverModeKey : _passengerModeKey;
    final modeString = _prefs.getString(key) ?? HeatMode.manual.name;
    return HeatModeExtension.fromString(modeString);
  }

  Future<void> setMode(UserType userType, HeatMode mode) async {
    final key = userType == UserType.driver ? _driverModeKey : _passengerModeKey;
    await _prefs.setString(key, mode.name);
  }

  int getHeatLevel(UserType userType) {
    final key = userType == UserType.driver ? _driverHeatLevelKey : _passengerHeatLevelKey;
    return _prefs.getInt(key) ?? 0;
  }

  Future<void> setHeatLevel(UserType userType, int level) async {
    final key = userType == UserType.driver ? _driverHeatLevelKey : _passengerHeatLevelKey;
    await _prefs.setInt(key, level);
  }

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
