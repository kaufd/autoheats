import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/models/manual_settings.dart';
import 'package:equatable/equatable.dart';

class Preset extends Equatable {
  final String id;
  final String name;
  final UserType userType;
  final ManualHeatSettings settings;
  final DateTime createdAt;
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

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'name': name,
      'userType': userType.name,
      'settings': {
        'autoHeatLevels': settings.autoHeatLevels
            .map((level) => {
                  'duration': level.duration,
                  'level': level.level,
                })
            .toList(),
        'temperatureThreshold': settings.temperatureThreshold,
      },
      'createdAt': createdAt.toIso8601String(),
      'lastUsed': lastUsed?.toIso8601String(),
    };
  }

  factory Preset.fromJson(Map<String, dynamic> json) {
    return Preset(
      id: json['id'],
      name: json['name'],
      userType: UserType.values.firstWhere(
        (type) => type.name == json['userType'],
        orElse: () => UserType.driver,
      ),
      settings: _settingsFromJson(json['settings']),
      createdAt: DateTime.parse(json['createdAt']),
      lastUsed: json['lastUsed'] != null ? DateTime.parse(json['lastUsed']) : null,
    );
  }

  static ManualHeatSettings _settingsFromJson(Map<String, dynamic> json) {
    return ManualHeatSettings(
      autoHeatLevels: (json['autoHeatLevels'] as List)
          .map((levelJson) => AutoHeatLevel(
                duration: levelJson['duration'],
                level: levelJson['level'],
              ))
          .toList(),
      temperatureThreshold: (json['temperatureThreshold'] as num).toDouble(),
    );
  }

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
