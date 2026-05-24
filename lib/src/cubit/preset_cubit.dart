// FILE: lib/src/cubit/preset_cubit.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Bloc-слой списка пресетов: load/save/delete/apply metadata.
//   SCOPE: PresetState, CRUD через PresetService,
//          selected preset per UserType.
//   DEPENDS: M-PRESET, M-ENUMS, M-MANUAL-SETTINGS
//   LINKS: M-PRESET, V-M-PRESET, FA-001, FA-011, DF-PRESET-APPLY
//   ROLE: RUNTIME
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   PresetCubit - Cubit<PresetState> для списка и selectedPreset per user
//   loadPresets(UserType) - загрузить пресеты одного сиденья
//   loadAllPresets - загрузить driver + passenger
//   savePreset - сохранить settings snapshot
//   updatePresetSettings - upsert settings on an existing Preset by id; reload list
//   deletePreset - удалить, очистить selection при совпадении и перезагрузить список
//   applyPreset - обновить lastUsed и selectedPreset metadata/persistence
//   clearError - очистить error state
//   PresetState - presets, selectedPresetByUser, selectedPreset, isLoading, error
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.4.0 - Mode-source decoupling: savePreset больше не принимает heatMode/heatLevel snapshot]
//   PREVIOUS_CHANGE: [v1.3.0 - Add updatePresetSettings for in-place editing of saved presets]
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
      final selectedPresets = await _selectedPresetsFor(presets, [userType]);
      final mergedSelectedPresets = {
        ...state.selectedPresets,
        ...selectedPresets,
      };
      emit(state.copyWith(
        presets: presets,
        selectedPresets: mergedSelectedPresets,
        selectedPreset: mergedSelectedPresets[userType],
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
      final selectedPresets =
          await _selectedPresetsFor(allPresets, UserType.values);
      emit(state.copyWith(
        presets: allPresets,
        selectedPresets: selectedPresets,
        selectedPreset: selectedPresets[UserType.driver] ??
            selectedPresets[UserType.passenger],
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
  //   PURPOSE: Сохранить пресет как snapshot settings.
  //   INPUTS: { name, userType, settings }
  //   OUTPUTS: { Future<void> }
  //   SIDE_EFFECTS: SharedPreferences write через PresetService, reload list.
  //   LINKS: M-PRESET, V-M-PRESET
  // END_CONTRACT: savePreset
  Future<void> savePreset({
    required String name,
    required UserType userType,
    required ManualHeatSettings settings,
  }) async {
    // START_BLOCK_SAVE_PRESET
    try {
      await _presetService.createPresetFromCurrentSettings(
        name: name,
        userType: userType,
        settings: settings,
      );

      await loadAllPresets();
    } catch (e) {
      emit(state.copyWith(error: e.toString()));
    }
    // END_BLOCK_SAVE_PRESET
  }

  // START_CONTRACT: updatePresetSettings
  //   PURPOSE: Persist new settings into an existing preset by id (keeps name/createdAt).
  //   INPUTS: { preset: Preset, settings: ManualHeatSettings }
  //   OUTPUTS: { Future<void> }
  //   SIDE_EFFECTS: SharedPreferences write через PresetService.savePreset upsert, reload list.
  //   LINKS: M-PRESET, V-M-PRESET, FA-011
  // END_CONTRACT: updatePresetSettings
  Future<void> updatePresetSettings(
    Preset preset,
    ManualHeatSettings settings,
  ) async {
    // START_BLOCK_UPDATE_PRESET_SETTINGS
    try {
      final updated = preset.copyWith(settings: settings);
      await _presetService.savePreset(updated);
      await loadAllPresets();
    } catch (e) {
      emit(state.copyWith(error: e.toString()));
    }
    // END_BLOCK_UPDATE_PRESET_SETTINGS
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
      await _presetService.setSelectedPresetId(preset.userType, preset.id);

      final updatedPresets = state.presets.map((p) {
        if (p.id == preset.id) {
          return p.copyWith(lastUsed: DateTime.now());
        }
        return p;
      }).toList();
      final selectedPresets = {
        ...state.selectedPresets,
        preset.userType: preset,
      };

      emit(state.copyWith(
        presets: updatedPresets,
        selectedPresets: selectedPresets,
        selectedPreset: preset,
      ));
    } catch (e) {
      emit(state.copyWith(error: e.toString()));
    }
  }

  void clearError() {
    emit(state.copyWith(clearError: true));
  }

  Future<Map<UserType, Preset?>> _selectedPresetsFor(
    List<Preset> presets,
    Iterable<UserType> users,
  ) async {
    final selectedPresets = <UserType, Preset?>{};

    for (final userType in users) {
      final selectedPresetId =
          await _presetService.getSelectedPresetId(userType);
      if (selectedPresetId == null) {
        selectedPresets[userType] = null;
        continue;
      }

      Preset? selectedPreset;
      for (final preset in presets) {
        if (preset.userType == userType && preset.id == selectedPresetId) {
          selectedPreset = preset;
          break;
        }
      }

      if (selectedPreset == null) {
        await _presetService.clearSelectedPresetId(userType);
      }
      selectedPresets[userType] = selectedPreset;
    }

    return selectedPresets;
  }
}

class PresetState extends Equatable {
  final List<Preset> presets;
  final Map<UserType, Preset?> selectedPresets;
  final Preset? selectedPreset;
  final bool isLoading;
  final String? error;

  const PresetState({
    this.presets = const [],
    this.selectedPresets = const {},
    this.selectedPreset,
    this.isLoading = false,
    this.error,
  });

  Preset? selectedPresetFor(UserType userType) => selectedPresets[userType];

  PresetState copyWith({
    List<Preset>? presets,
    Map<UserType, Preset?>? selectedPresets,
    Preset? selectedPreset,
    bool? isLoading,
    String? error,
    bool clearError = false,
  }) {
    return PresetState(
      presets: presets ?? this.presets,
      selectedPresets: selectedPresets ?? this.selectedPresets,
      selectedPreset: selectedPreset ?? this.selectedPreset,
      isLoading: isLoading ?? this.isLoading,
      error: clearError ? null : (error ?? this.error),
    );
  }

  @override
  List<Object?> get props => [
        presets,
        selectedPresets,
        selectedPreset,
        isLoading,
        error,
      ];
}
