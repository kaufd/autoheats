// FILE: lib/src/services/preset_service.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: JSON-CRUD пользовательских пресетов в SharedPreferences.
//   SCOPE: load/save/delete presets per UserType, createPresetFromCurrentSettings,
//          lastUsed metadata, runtime heatMode/heatLevel persistence,
//          selected preset id per UserType.
//   DEPENDS: M-ENUMS, M-MANUAL-SETTINGS
//   LINKS: M-PRESET, V-M-PRESET, FA-001, FA-011, DF-PRESET-APPLY
//   ROLE: RUNTIME
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   PresetService - persistence API для Preset
//   getPresets(UserType) - список пресетов одного сиденья
//   getAllPresets - driver + passenger
//   savePreset - insert/update by id
//   deletePreset - remove by id/userType
//   getPresetById - nullable lookup
//   getSelectedPresetId/setSelectedPresetId/clearSelectedPresetId - selection per UserType
//   updatePresetLastUsed - обновить metadata
//   createPresetFromCurrentSettings - snapshot settings + heatMode + heatLevel
//   _savePresets - JSON encode + SharedPreferences write
//   clearAllPresets - очистить оба списка
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.2.0 - Phase-4 Slice-9: selected preset id persists per user]
//   PREVIOUS_CHANGE: [v1.1.0 - Phase-4 Slice-1: Preset сохраняет runtime heatMode/heatLevel]
// END_CHANGE_SUMMARY

import 'dart:convert';

import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/models/manual_settings.dart';
import 'package:autoheat/src/models/preset.dart';
import 'package:shared_preferences/shared_preferences.dart';

class PresetService {
  final SharedPreferences _prefs;

  PresetService(this._prefs);

  static const String _driverPresetsKey = 'driver_presets';
  static const String _passengerPresetsKey = 'passenger_presets';
  static const String _driverSelectedPresetKey = 'driver_selected_preset_id';
  static const String _passengerSelectedPresetKey =
      'passenger_selected_preset_id';

  Future<List<Preset>> getPresets(UserType userType) async {
    final key =
        userType == UserType.driver ? _driverPresetsKey : _passengerPresetsKey;
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

    final existingIndex = presets.indexWhere((p) => p.id == preset.id);

    if (existingIndex != -1) {
      presets[existingIndex] = preset;
    } else {
      presets.add(preset);
    }

    await _savePresets(presets, preset.userType);
  }

  Future<void> deletePreset(String presetId, UserType userType) async {
    final presets = await getPresets(userType);
    presets.removeWhere((preset) => preset.id == presetId);
    await _savePresets(presets, userType);

    final selectedPresetId = await getSelectedPresetId(userType);
    if (selectedPresetId == presetId) {
      await clearSelectedPresetId(userType);
    }
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
      presets[presetIndex] =
          presets[presetIndex].copyWith(lastUsed: DateTime.now());
      await _savePresets(presets, userType);
    }
  }

  // START_CONTRACT: getSelectedPresetId
  //   PURPOSE: Прочитать persisted selected preset id для сиденья.
  //   INPUTS: { userType: UserType }
  //   OUTPUTS: { Future<String?> }
  //   SIDE_EFFECTS: none.
  //   LINKS: M-PRESET, FA-011, DF-PRESET-APPLY
  // END_CONTRACT: getSelectedPresetId
  Future<String?> getSelectedPresetId(UserType userType) async {
    // START_BLOCK_SELECTED_PRESET_ID
    return _prefs.getString(_selectedPresetKey(userType));
    // END_BLOCK_SELECTED_PRESET_ID
  }

  Future<void> setSelectedPresetId(UserType userType, String presetId) async {
    await _prefs.setString(_selectedPresetKey(userType), presetId);
  }

  Future<void> clearSelectedPresetId(UserType userType) async {
    await _prefs.remove(_selectedPresetKey(userType));
  }

  // START_CONTRACT: createPresetFromCurrentSettings
  //   PURPOSE: Создать snapshot пресета из текущих settings + runtime mode/level.
  //   INPUTS: { name, userType, settings, heatMode, heatLevel }
  //   OUTPUTS: { Future<Preset> }
  //   SIDE_EFFECTS: SharedPreferences write через savePreset.
  //   LINKS: M-PRESET, M-MODE, V-M-PRESET, FA-001, FA-011
  // END_CONTRACT: createPresetFromCurrentSettings
  Future<Preset> createPresetFromCurrentSettings({
    required String name,
    required UserType userType,
    required ManualHeatSettings settings,
    required HeatMode heatMode,
    required int heatLevel,
  }) async {
    // START_BLOCK_CREATE_PRESET_FROM_CURRENT_SETTINGS
    final preset = Preset(
      id: DateTime.now().millisecondsSinceEpoch.toString(),
      name: name,
      userType: userType,
      settings: settings,
      heatMode: heatMode,
      heatLevel: heatLevel.clamp(0, 3),
      createdAt: DateTime.now(),
    );

    await savePreset(preset);
    return preset;
    // END_BLOCK_CREATE_PRESET_FROM_CURRENT_SETTINGS
  }

  Future<void> _savePresets(List<Preset> presets, UserType userType) async {
    final key =
        userType == UserType.driver ? _driverPresetsKey : _passengerPresetsKey;
    final presetsJson =
        json.encode(presets.map((preset) => preset.toJson()).toList());
    await _prefs.setString(key, presetsJson);
  }

  Future<void> clearAllPresets() async {
    await _prefs.remove(_driverPresetsKey);
    await _prefs.remove(_passengerPresetsKey);
    await _prefs.remove(_driverSelectedPresetKey);
    await _prefs.remove(_passengerSelectedPresetKey);
  }

  String _selectedPresetKey(UserType userType) {
    return userType == UserType.driver
        ? _driverSelectedPresetKey
        : _passengerSelectedPresetKey;
  }
}
