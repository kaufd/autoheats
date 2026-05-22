// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'manual_settings.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ManualHeatSettings _$ManualHeatSettingsFromJson(Map<String, dynamic> json) =>
    ManualHeatSettings(
      autoHeatLevels: (json['autoHeatLevels'] as List<dynamic>)
          .map((e) => AutoHeatLevel.fromJson(e as Map<String, dynamic>))
          .toList(),
      temperatureThreshold: (json['temperatureThreshold'] as num).toDouble(),
    );

Map<String, dynamic> _$ManualHeatSettingsToJson(ManualHeatSettings instance) =>
    <String, dynamic>{
      'autoHeatLevels': instance.autoHeatLevels.map((e) => e.toJson()).toList(),
      'temperatureThreshold': instance.temperatureThreshold,
    };

AutoHeatLevel _$AutoHeatLevelFromJson(Map<String, dynamic> json) =>
    AutoHeatLevel(
      duration: (json['duration'] as num).toInt(),
      level: (json['level'] as num).toInt(),
    );

Map<String, dynamic> _$AutoHeatLevelToJson(AutoHeatLevel instance) =>
    <String, dynamic>{
      'duration': instance.duration,
      'level': instance.level,
    };
