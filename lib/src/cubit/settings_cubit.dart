// FILE: lib/src/cubit/settings_cubit.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Expose persisted global settings as reactive Cubit state.
//   SCOPE: initialize, cabin-temperature visibility, and debug-mode toggles.
//   DEPENDS: M-SETTINGS
//   LINKS: M-SETTINGS, M-UI-SETTINGS, V-M-SETTINGS
//   MAP_MODE: SUMMARY
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   SettingsState - showCabinTemperature and debugMode state
//   SettingsCubit - initialize and persistence-backed setting transitions
// END_MODULE_MAP

import 'package:autoheat/src/services/settings_service.dart';
import 'package:equatable/equatable.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

class SettingsState extends Equatable {
  final bool showCabinTemperature;
  final bool debugMode;

  const SettingsState({
    this.showCabinTemperature = true,
    this.debugMode = false,
  });

  SettingsState copyWith({
    bool? showCabinTemperature,
    bool? debugMode,
  }) {
    return SettingsState(
      showCabinTemperature: showCabinTemperature ?? this.showCabinTemperature,
      debugMode: debugMode ?? this.debugMode,
    );
  }

  @override
  List<Object?> get props => [showCabinTemperature, debugMode];
}

class SettingsCubit extends Cubit<SettingsState> {
  final SettingsService _settingsService;

  SettingsCubit(this._settingsService) : super(const SettingsState());

  Future<void> initialize() async {
    emit(state.copyWith(
      showCabinTemperature: _settingsService.getShowCabinTemperature(),
      debugMode: _settingsService.getDebugMode(),
    ));
  }

  Future<void> toggleCabinTemperatureVisibility() async {
    final newValue = !state.showCabinTemperature;
    emit(state.copyWith(showCabinTemperature: newValue));
    await _settingsService.setShowCabinTemperature(newValue);
  }

  Future<void> setCabinTemperatureVisibility(bool show) async {
    if (state.showCabinTemperature == show) return;
    emit(state.copyWith(showCabinTemperature: show));
    await _settingsService.setShowCabinTemperature(show);
  }

  Future<void> toggleDebugMode() async {
    final newValue = !state.debugMode;
    emit(state.copyWith(debugMode: newValue));
    await _settingsService.setDebugMode(newValue);
  }
}
