// FILE: lib/src/app_enums.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Стабильные enum-ы режима подогрева и типа сиденья + строковые
//            расширения для сериализации в SharedPreferences.
//   SCOPE: HeatMode, UserType, fromString-десериализация по .name.
//   DEPENDS: none
//   LINKS: M-ENUMS, V-M-ENUMS
//   ROLE: TYPES
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   HeatMode - enum manual|presets|auto (имена — публичный контракт prefs)
//   UserType - enum driver|passenger (имена — публичный контракт prefs)
//   UserTypeExtension.fromString - String -> UserType; orElse driver
//   HeatModeExtension.fromString - String -> HeatMode; orElse manual
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v0.2.0 - GRACE-инициализация: добавлены MODULE_CONTRACT и MODULE_MAP]
// END_CHANGE_SUMMARY

enum HeatMode { manual, presets, auto }

enum UserType { driver, passenger }

extension UserTypeExtension on UserType {
  static UserType fromString(String value) {
    return UserType.values.firstWhere(
      (type) => type.name == value,
      orElse: () => UserType.driver,
    );
  }
}

extension HeatModeExtension on HeatMode {
  static HeatMode fromString(String value) {
    return HeatMode.values.firstWhere(
      (mode) => mode.name == value,
      orElse: () => HeatMode.manual,
    );
  }
}
