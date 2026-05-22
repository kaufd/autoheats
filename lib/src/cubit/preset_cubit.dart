// FILE: lib/src/cubit/preset_cubit.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Bloc-слой списка пресетов: load/save/delete/apply metadata.
//   SCOPE: PresetState, CRUD через PresetService, сохранение runtime heatMode/heatLevel.
//   DEPENDS: M-PRESET, M-ENUMS, M-MANUAL-SETTINGS
//   LINKS: M-PRESET, V-M-PRESET, FA-001, FA-011, DF-PRESET-APPLY
//   ROLE: RUNTIME
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   PresetCubit - Cubit<PresetState> для списка и selectedPreset
//   loadPresets(UserType) - загрузить пресеты одного сиденья
//   loadAllPresets - загрузить driver + passenger
//   savePreset - сохранить settings + runtime mode/level snapshot
//   deletePreset - удалить и перезагрузить список
//   applyPreset - обновить lastUsed и selectedPreset metadata
//   clearError - очистить error state
//   PresetState - presets, selectedPreset, isLoading, error
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.1.0 - Phase-4 Slice-1: savePreset принимает heatMode/heatLevel]
// END_CHANGE_SUMMARY

import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/models/manual_settings.dart';
import 'package:autoheat/src/models/preset.dart';
import 'package:autoheat/src/services/preset_service.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:equatable/equatable.dart';

class PresetCubit extends Cubit<PresetState> {
  final PresetService _presetService;

  PresetCubit(this._presetService) : super(const PresetState());

  Future<void> loadPresets(UserType userType) async {
    emit(state.copyWith(isLoading: true));

    try {
      final presets = await _presetService.getPresets(userType);
      emit(state.copyWith(
        presets: presets,
        isLoading: false,
      ));
    } catch (e) {
      emit(state.copyWith(
        isLoading: false,
        error: e.toString(),
      ));
    }
  }

  Future<void> loadAllPresets() async {
    emit(state.copyWith(isLoading: true));

    try {
      final allPresets = await _presetService.getAllPresets();
      emit(state.copyWith(
        presets: allPresets,
        isLoading: false,
      ));
    } catch (e) {
      emit(state.copyWith(
        isLoading: false,
        error: e.toString(),
      ));
    }
  }

  // START_CONTRACT: savePreset
  //   PURPOSE: Сохранить пресет как snapshot settings + runtime mode/level.
  //   INPUTS: { name, userType, settings, heatMode, heatLevel }
  //   OUTPUTS: { Future<void> }
  //   SIDE_EFFECTS: SharedPreferences write через PresetService, reload list.
  //   LINKS: M-PRESET, M-MODE, V-M-PRESET, FA-001, FA-011
  // END_CONTRACT: savePreset
  Future<void> savePreset({
    required String name,
    required UserType userType,
    required ManualHeatSettings settings,
    required HeatMode heatMode,
    required int heatLevel,
  }) async {
    // START_BLOCK_SAVE_PRESET
    try {
      await _presetService.createPresetFromCurrentSettings(
        name: name,
        userType: userType,
        settings: settings,
        heatMode: heatMode,
        heatLevel: heatLevel,
      );

      await loadAllPresets();
    } catch (e) {
      emit(state.copyWith(error: e.toString()));
    }
    // END_BLOCK_SAVE_PRESET
  }

  Future<void> deletePreset(String presetId, UserType userType) async {
    try {
      await _presetService.deletePreset(presetId, userType);
      await loadAllPresets();
    } catch (e) {
      emit(state.copyWith(error: e.toString()));
    }
  }

  Future<void> applyPreset(Preset preset) async {
    try {
      await _presetService.updatePresetLastUsed(preset.id, preset.userType);

      final updatedPresets = state.presets.map((p) {
        if (p.id == preset.id) {
          return p.copyWith(lastUsed: DateTime.now());
        }
        return p;
      }).toList();

      emit(state.copyWith(
        presets: updatedPresets,
        selectedPreset: preset,
      ));
    } catch (e) {
      emit(state.copyWith(error: e.toString()));
    }
  }

  void clearError() {
    emit(state.copyWith(clearError: true));
  }
}

class PresetState extends Equatable {
  final List<Preset> presets;
  final Preset? selectedPreset;
  final bool isLoading;
  final String? error;

  const PresetState({
    this.presets = const [],
    this.selectedPreset,
    this.isLoading = false,
    this.error,
  });

  PresetState copyWith({
    List<Preset>? presets,
    Preset? selectedPreset,
    bool? isLoading,
    String? error,
    bool clearError = false,
  }) {
    return PresetState(
      presets: presets ?? this.presets,
      selectedPreset: selectedPreset ?? this.selectedPreset,
      isLoading: isLoading ?? this.isLoading,
      error: clearError ? null : (error ?? this.error),
    );
  }

  @override
  List<Object?> get props => [
        presets,
        selectedPreset,
        isLoading,
        error,
      ];
}
