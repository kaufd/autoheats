// FILE: lib/src/models/preset.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: JSON-модель пользовательского пресета: настройки, runtime mode/level
//            и metadata для списка/применения пресетов.
//   SCOPE: Preset, JSON adapters для UserType/HeatMode/DateTime, legacy defaults.
//   DEPENDS: M-ENUMS, M-MANUAL-SETTINGS
//   LINKS: M-PRESET, V-M-PRESET, FA-001, FA-011, DF-PRESET-APPLY
//   ROLE: TYPES
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   Preset - immutable value object с id/name/userType/settings/heatMode/heatLevel
//   copyWith - изменить поля без потери metadata
//   fromJson/toJson - SharedPreferences JSON contract через json_serializable
//   _userTypeFromJson/_userTypeToJson - stable enum.name adapter
//   _heatModeFromJson/_heatModeToJson - stable enum.name adapter + legacy presets default
//   _heatLevelFromJson - legacy/clamped heatLevel adapter
//   _dateTimeFromJson/_dateTimeToJson - ISO-8601 adapter
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.1.0 - Phase-4 Slice-1: добавлены runtime heatMode/heatLevel]
// END_CHANGE_SUMMARY

import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/models/manual_settings.dart';
import 'package:equatable/equatable.dart';
import 'package:json_annotation/json_annotation.dart';

part 'preset.g.dart';

@JsonSerializable(explicitToJson: true)
class Preset extends Equatable {
  final String id;
  final String name;
  @JsonKey(fromJson: _userTypeFromJson, toJson: _userTypeToJson)
  final UserType userType;
  final ManualHeatSettings settings;
  @JsonKey(fromJson: _heatModeFromJson, toJson: _heatModeToJson)
  final HeatMode heatMode;
  @JsonKey(fromJson: _heatLevelFromJson)
  final int heatLevel;
  @JsonKey(fromJson: _dateTimeFromJson, toJson: _dateTimeToJson)
  final DateTime createdAt;
  @JsonKey(fromJson: _dateTimeNullableFromJson, toJson: _dateTimeNullableToJson)
  final DateTime? lastUsed;

  const Preset({
    required this.id,
    required this.name,
    required this.userType,
    required this.settings,
    this.heatMode = HeatMode.presets,
    this.heatLevel = 0,
    required this.createdAt,
    this.lastUsed,
  });

  Preset copyWith({
    String? id,
    String? name,
    UserType? userType,
    ManualHeatSettings? settings,
    HeatMode? heatMode,
    int? heatLevel,
    DateTime? createdAt,
    DateTime? lastUsed,
  }) {
    return Preset(
      id: id ?? this.id,
      name: name ?? this.name,
      userType: userType ?? this.userType,
      settings: settings ?? this.settings,
      heatMode: heatMode ?? this.heatMode,
      heatLevel: heatLevel ?? this.heatLevel,
      createdAt: createdAt ?? this.createdAt,
      lastUsed: lastUsed ?? this.lastUsed,
    );
  }

  factory Preset.fromJson(Map<String, dynamic> json) => _$PresetFromJson(json);
  Map<String, dynamic> toJson() => _$PresetToJson(this);

  @override
  List<Object?> get props => [
        id,
        name,
        userType,
        settings,
        heatMode,
        heatLevel,
        createdAt,
        lastUsed,
      ];
}

UserType _userTypeFromJson(String json) {
  return UserType.values.firstWhere(
    (type) => type.name == json,
    orElse: () => UserType.driver,
  );
}

String _userTypeToJson(UserType userType) {
  return userType.name;
}

HeatMode _heatModeFromJson(String? json) {
  if (json == null) return HeatMode.presets;
  return HeatModeExtension.fromString(json);
}

String _heatModeToJson(HeatMode heatMode) {
  return heatMode.name;
}

int _heatLevelFromJson(Object? json) {
  if (json is num) return json.toInt().clamp(0, 3);
  return 0;
}

DateTime _dateTimeFromJson(String json) {
  return DateTime.parse(json);
}

String _dateTimeToJson(DateTime dateTime) {
  return dateTime.toIso8601String();
}

DateTime? _dateTimeNullableFromJson(String? json) {
  return json != null ? DateTime.parse(json) : null;
}

String? _dateTimeNullableToJson(DateTime? dateTime) {
  return dateTime?.toIso8601String();
}
