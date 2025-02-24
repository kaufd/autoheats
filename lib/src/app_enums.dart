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
