import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/models/preset.dart';
import 'package:autoheat/src/models/manual_settings.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'dart:convert';

class PresetService {
  final SharedPreferences _prefs;

  PresetService(this._prefs);

  static const String _driverPresetsKey = 'driver_presets';
  static const String _passengerPresetsKey = 'passenger_presets';

  Future<List<Preset>> getPresets(UserType userType) async {
    final key = userType == UserType.driver ? _driverPresetsKey : _passengerPresetsKey;
    final presetsJson = _prefs.getString(key);

    if (presetsJson == null) {
      return [];
    }

    try {
      final List<dynamic> presetsList = json.decode(presetsJson);
      return presetsList.map((json) => Preset.fromJson(json)).toList();
    } catch (e) {
      return [];
    }
  }

  Future<List<Preset>> getAllPresets() async {
    final driverPresets = await getPresets(UserType.driver);
    final passengerPresets = await getPresets(UserType.passenger);
    return [...driverPresets, ...passengerPresets];
  }

  Future<void> savePreset(Preset preset) async {
    final presets = await getPresets(preset.userType);

    // Проверяем, существует ли пресет с таким же ID
    final existingIndex = presets.indexWhere((p) => p.id == preset.id);

    if (existingIndex != -1) {
      // Обновляем существующий пресет
      presets[existingIndex] = preset;
    } else {
      // Добавляем новый пресет
      presets.add(preset);
    }

    await _savePresets(presets, preset.userType);
  }

  Future<void> deletePreset(String presetId, UserType userType) async {
    final presets = await getPresets(userType);
    presets.removeWhere((preset) => preset.id == presetId);
    await _savePresets(presets, userType);
  }

  Future<Preset?> getPresetById(String presetId, UserType userType) async {
    final presets = await getPresets(userType);
    try {
      return presets.firstWhere((preset) => preset.id == presetId);
    } catch (e) {
      return null;
    }
  }

  Future<void> updatePresetLastUsed(String presetId, UserType userType) async {
    final presets = await getPresets(userType);
    final presetIndex = presets.indexWhere((preset) => preset.id == presetId);

    if (presetIndex != -1) {
      presets[presetIndex] = presets[presetIndex].copyWith(lastUsed: DateTime.now());
      await _savePresets(presets, userType);
    }
  }

  Future<Preset> createPresetFromCurrentSettings({
    required String name,
    required UserType userType,
    required ManualHeatSettings settings,
  }) async {
    final preset = Preset(
      id: DateTime.now().millisecondsSinceEpoch.toString(),
      name: name,
      userType: userType,
      settings: settings,
      createdAt: DateTime.now(),
    );

    await savePreset(preset);
    return preset;
  }

  Future<void> _savePresets(List<Preset> presets, UserType userType) async {
    final key = userType == UserType.driver ? _driverPresetsKey : _passengerPresetsKey;
    final presetsJson = json.encode(presets.map((preset) => preset.toJson()).toList());
    await _prefs.setString(key, presetsJson);
  }

  Future<void> clearAllPresets() async {
    await _prefs.remove(_driverPresetsKey);
    await _prefs.remove(_passengerPresetsKey);
  }
}
