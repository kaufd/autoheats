import 'package:autoheat/src/app_enums.dart';

class ManualHeatSettings {
  final UserType userType;
  final List<AutoHeatLevel> autoHeatLevels;
  final double temperatureThreshold;

  const ManualHeatSettings({
    required this.userType,
    required this.autoHeatLevels,
    required this.temperatureThreshold,
  });

  ManualHeatSettings copyWith({
    UserType? userType,
    List<AutoHeatLevel>? autoHeatLevels,
    double? temperatureThreshold,
  }) {
    return ManualHeatSettings(
      userType: userType ?? this.userType,
      autoHeatLevels: autoHeatLevels ?? this.autoHeatLevels,
      temperatureThreshold: temperatureThreshold ?? this.temperatureThreshold,
    );
  }

  factory ManualHeatSettings.defaultFor(UserType userType) {
    return ManualHeatSettings(
      userType: userType,
      autoHeatLevels: [
        AutoHeatLevel(duration: 2, level: 1), // Уровень 1: 2 мин работы
        AutoHeatLevel(duration: 5, level: 2), // Уровень 2: 5 мин работы
        AutoHeatLevel(duration: 10, level: 3), // Уровень 3: 10 мин работы
      ],
      temperatureThreshold: 5.0, // 5°C
    );
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is ManualHeatSettings &&
        other.userType == userType &&
        other.autoHeatLevels == autoHeatLevels &&
        other.temperatureThreshold == temperatureThreshold;
  }

  @override
  int get hashCode {
    return userType.hashCode ^ autoHeatLevels.hashCode ^ temperatureThreshold.hashCode;
  }
}

class AutoHeatLevel {
  final int duration; // в минутах
  final int level; // уровень нагрева (1-5)

  const AutoHeatLevel({
    required this.duration,
    required this.level,
  });

  AutoHeatLevel copyWith({
    int? duration,
    int? level,
  }) {
    return AutoHeatLevel(
      duration: duration ?? this.duration,
      level: level ?? this.level,
    );
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is AutoHeatLevel && other.duration == duration && other.level == level;
  }

  @override
  int get hashCode => duration.hashCode ^ level.hashCode;

  @override
  String toString() {
    return 'AutoHeatLevel(duration: $duration, level: $level)';
  }
}

class ManualSettingsState {
  final ManualHeatSettings driverSettings;
  final ManualHeatSettings passengerSettings;
  final bool isLoading;
  final String? error;

  const ManualSettingsState({
    required this.driverSettings,
    required this.passengerSettings,
    this.isLoading = false,
    this.error,
  });

  ManualSettingsState copyWith({
    ManualHeatSettings? driverSettings,
    ManualHeatSettings? passengerSettings,
    bool? isLoading,
    String? error,
  }) {
    return ManualSettingsState(
      driverSettings: driverSettings ?? this.driverSettings,
      passengerSettings: passengerSettings ?? this.passengerSettings,
      isLoading: isLoading ?? this.isLoading,
      error: error ?? this.error,
    );
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is ManualSettingsState &&
        other.driverSettings == driverSettings &&
        other.passengerSettings == passengerSettings &&
        other.isLoading == isLoading &&
        other.error == error;
  }

  @override
  int get hashCode {
    return driverSettings.hashCode ^
        passengerSettings.hashCode ^
        isLoading.hashCode ^
        error.hashCode;
  }
}
