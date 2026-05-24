// FILE: lib/src/models/preset.dart
// VERSION: 1.2.0
// START_MODULE_CONTRACT
//   PURPOSE: JSON-модель пользовательского пресета: name, settings и metadata.
//   SCOPE: Preset, JSON adapters для UserType/DateTime. Snapshot-поля
//          heatMode/heatLevel удалены — пресет описывает только настройки,
//          mode/level определяются runtime'ом при apply.
//   DEPENDS: M-ENUMS, M-MANUAL-SETTINGS
//   LINKS: M-PRESET, V-M-PRESET, DF-PRESET-APPLY
//   ROLE: TYPES
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   Preset - immutable value object с id/name/userType/settings/createdAt/lastUsed
//   copyWith - изменить поля без потери metadata
//   fromJson/toJson - SharedPreferences JSON contract через json_serializable
//   _userTypeFromJson/_userTypeToJson - stable enum.name adapter
//   _dateTimeFromJson/_dateTimeToJson - ISO-8601 adapter
//   _dateTimeNullableFromJson/_dateTimeNullableToJson - nullable ISO-8601 adapter
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.2.0 - Mode-source decoupling: drop heatMode/heatLevel snapshot fields]
//   PREVIOUS_CHANGE: [v1.1.0 - Phase-4 Slice-1: добавлены runtime heatMode/heatLevel]
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
  @JsonKey(fromJson: _dateTimeFromJson, toJson: _dateTimeToJson)
  final DateTime createdAt;
  @JsonKey(fromJson: _dateTimeNullableFromJson, toJson: _dateTimeNullableToJson)
  final DateTime? lastUsed;

  const Preset({
    required this.id,
    required this.name,
    required this.userType,
    required this.settings,
    required this.createdAt,
    this.lastUsed,
  });

  Preset copyWith({
    String? id,
    String? name,
    UserType? userType,
    ManualHeatSettings? settings,
    DateTime? createdAt,
    DateTime? lastUsed,
  }) {
    return Preset(
      id: id ?? this.id,
      name: name ?? this.name,
      userType: userType ?? this.userType,
      settings: settings ?? this.settings,
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

String _userTypeToJson(UserType userType) => userType.name;

DateTime _dateTimeFromJson(String json) => DateTime.parse(json);
String _dateTimeToJson(DateTime dateTime) => dateTime.toIso8601String();

DateTime? _dateTimeNullableFromJson(String? json) {
  return json != null ? DateTime.parse(json) : null;
}

String? _dateTimeNullableToJson(DateTime? dateTime) {
  return dateTime?.toIso8601String();
}
