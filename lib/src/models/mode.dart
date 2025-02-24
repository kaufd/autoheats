import 'package:realm/realm.dart';

part 'mode.realm.dart';

@RealmModel()
class _Mode {
  late String userName;
  late String modeName;

  UserType get user => UserType.values.firstWhere((e) => e.name == userName);

  HeatMode get mode => HeatMode.values.firstWhere((e) => e.name == modeName);

  set user(UserType value) {
    userName = value.name;
  }

  set mode(HeatMode value) {
    modeName = value.name;
  }
}

enum HeatMode { off, manual, auto }

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
      orElse: () => HeatMode.off,
    );
  }
}
