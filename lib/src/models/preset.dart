import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/models/manual_settings.dart';
import 'package:equatable/equatable.dart';
import 'package:json_annotation/json_annotation.dart';

part 'preset.g.dart';

@JsonSerializable()
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

String _userTypeToJson(UserType userType) {
  return userType.name;
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
