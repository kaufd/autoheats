// FILE: lib/src/models/manual_settings.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: JSON-модели настроек авторежима: ManualHeatSettings + AutoHeatLevel.
//   SCOPE: ManualHeatSettings (durations+threshold), AutoHeatLevel, JSON contract.
//   DEPENDS: M-ENUMS, M-CONSTANTS-TEMPERATURE
//   LINKS: M-MANUAL-SETTINGS, M-PRESET, V-M-PRESET
//   ROLE: TYPES
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   ManualHeatSettings - durations уровней и temperatureThreshold
//   ManualHeatSettings.defaultFor - дефолтные настройки UserType
//   AutoHeatLevel - duration + level
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.3.0 - Mode-source decoupling: ManualSettingsState класс удалён вместе с ManualSettingsCubit]
//   PREVIOUS_CHANGE: [v1.2.0 - Phase-4 Slice-6: ManualSettingsState.copyWith supports clearError]
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
