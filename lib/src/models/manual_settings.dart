// FILE: lib/src/models/manual_settings.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Модель ручных настроек/параметров пресета для driver/passenger.
//   SCOPE: ManualHeatSettings, AutoHeatLevel, ManualSettingsState, JSON contract.
//   DEPENDS: M-ENUMS, M-CONSTANTS-TEMPERATURE
//   LINKS: M-MANUAL-SETTINGS, V-M-MANUAL-SETTINGS, M-PRESET, FA-001
//   ROLE: TYPES
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   ManualHeatSettings - durations уровней и temperatureThreshold
//   ManualHeatSettings.defaultFor - дефолтные настройки UserType
//   AutoHeatLevel - duration + level
//   ManualSettingsState - Cubit state для driver/passenger settings
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.1.0 - Phase-4 Slice-1: explicitToJson для вложенных пресетов]
// END_CHANGE_SUMMARY

import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/constants/temperature_constants.dart';
import 'package:equatable/equatable.dart';
import 'package:json_annotation/json_annotation.dart';

part 'manual_settings.g.dart';

@JsonSerializable(explicitToJson: true)
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

  factory ManualHeatSettings.fromJson(Map<String, dynamic> json) =>
      _$ManualHeatSettingsFromJson(json);
  Map<String, dynamic> toJson() => _$ManualHeatSettingsToJson(this);

  @override
  List<Object?> get props => [autoHeatLevels, temperatureThreshold];
}

@JsonSerializable()
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

  factory AutoHeatLevel.fromJson(Map<String, dynamic> json) =>
      _$AutoHeatLevelFromJson(json);
  Map<String, dynamic> toJson() => _$AutoHeatLevelToJson(this);

  @override
  List<Object?> get props => [
        duration,
        level,
      ];
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
