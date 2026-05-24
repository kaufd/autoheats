// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'preset.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

Preset _$PresetFromJson(Map<String, dynamic> json) => Preset(
      id: json['id'] as String,
      name: json['name'] as String,
      userType: _userTypeFromJson(json['userType'] as String),
      settings:
          ManualHeatSettings.fromJson(json['settings'] as Map<String, dynamic>),
      createdAt: _dateTimeFromJson(json['createdAt'] as String),
      lastUsed: _dateTimeNullableFromJson(json['lastUsed'] as String?),
    );

Map<String, dynamic> _$PresetToJson(Preset instance) => <String, dynamic>{
      'id': instance.id,
      'name': instance.name,
      'userType': _userTypeToJson(instance.userType),
      'settings': instance.settings.toJson(),
      'createdAt': _dateTimeToJson(instance.createdAt),
      'lastUsed': _dateTimeNullableToJson(instance.lastUsed),
    };
