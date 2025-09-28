import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/constants/temperature_constants.dart';
import 'package:equatable/equatable.dart';

class ManualHeatSettings extends Equatable {
  final List<AutoHeatLevel> autoHeatLevels;
  final double temperatureThreshold;

  const ManualHeatSettings({
    required this.autoHeatLevels,
    required this.temperatureThreshold,
  });

  ManualHeatSettings copyWith({
    List<AutoHeatLevel>? autoHeatLevels,
    double? temperatureThreshold,
  }) {
    return ManualHeatSettings(
      autoHeatLevels: autoHeatLevels ?? this.autoHeatLevels,
      temperatureThreshold: temperatureThreshold ?? this.temperatureThreshold,
    );
  }

  factory ManualHeatSettings.defaultFor(UserType userType) {
    return ManualHeatSettings(
      autoHeatLevels: [
        AutoHeatLevel(duration: 2, level: 1), // Уровень 1: 2 мин работы
        AutoHeatLevel(duration: 5, level: 2), // Уровень 2: 5 мин работы
        AutoHeatLevel(duration: 10, level: 3), // Уровень 3: 10 мин работы
      ],
      temperatureThreshold: TemperatureConstants.defaultTemperatureThreshold,
    );
  }

  @override
  List<Object?> get props => [autoHeatLevels, temperatureThreshold];
}

class AutoHeatLevel extends Equatable {
  final int duration;
  final int level;

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
  List<Object?> get props => [duration, level];

  @override
  String toString() {
    return 'AutoHeatLevel(duration: $duration, level: $level)';
  }
}

class ManualSettingsState extends Equatable {
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
  List<Object?> get props => [
        driverSettings,
        passengerSettings,
        isLoading,
        error,
      ];
}
