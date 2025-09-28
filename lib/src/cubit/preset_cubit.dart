import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/models/preset.dart';
import 'package:autoheat/src/models/manual_settings.dart';
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

  Future<void> savePreset({
    required String name,
    required UserType userType,
    required ManualHeatSettings settings,
  }) async {
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
    emit(state.copyWith(error: null));
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
  }) {
    return PresetState(
      presets: presets ?? this.presets,
      selectedPreset: selectedPreset ?? this.selectedPreset,
      isLoading: isLoading ?? this.isLoading,
      error: error ?? this.error,
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
