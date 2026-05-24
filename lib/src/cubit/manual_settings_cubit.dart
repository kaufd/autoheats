// FILE: lib/src/cubit/manual_settings_cubit.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Bloc-слой ручных настроек driver/passenger.
//   SCOPE: initialize, updateAutoHeatLevel, updateTemperatureThreshold, resetToDefaults,
//          applyPresetSettings, validation and transient error state.
//   DEPENDS: M-MANUAL-SETTINGS, M-ENUMS
//   LINKS: M-MANUAL-SETTINGS, V-M-MANUAL-SETTINGS, FA-012
//   ROLE: RUNTIME
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   ManualSettingsCubit - Cubit<ManualSettingsState>
//   initialize - load and validate persisted driver/passenger settings
//   updateAutoHeatLevel - optimistic duration update + save
//   updateTemperatureThreshold - optimistic threshold update + save
//   resetToDefaults - restore default settings for one UserType
//   applyPresetSettings - apply both seats from preset payload
//   _validateSettings - clamp auto heat durations to supported range
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.1.0 - Phase-4 Slice-6: successful operations clear stale errors]
// END_CHANGE_SUMMARY

import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/models/manual_settings.dart';
import 'package:autoheat/src/services/manual_settings_service.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

class ManualSettingsCubit extends Cubit<ManualSettingsState> {
  final ManualSettingsService _settingsService;

  ManualSettingsCubit(this._settingsService)
      : super(
          ManualSettingsState(
            driverSettings: ManualHeatSettings.defaultFor(UserType.driver),
            passengerSettings:
                ManualHeatSettings.defaultFor(UserType.passenger),
          ),
        );

  Future<void> initialize() async {
    emit(state.copyWith(isLoading: true));

    try {
      final driverSettings =
          await _settingsService.getSettings(UserType.driver);
      final passengerSettings =
          await _settingsService.getSettings(UserType.passenger);

      final validatedDriverSettings = _validateSettings(driverSettings);
      final validatedPassengerSettings = _validateSettings(passengerSettings);

      emit(state.copyWith(
        driverSettings: validatedDriverSettings,
        passengerSettings: validatedPassengerSettings,
        isLoading: false,
        clearError: true,
      ));
    } catch (e) {
      emit(state.copyWith(
        isLoading: false,
        error: e.toString(),
      ));
    }
  }

  Future<void> updateAutoHeatLevel(
    UserType userType,
    AutoHeatLevel autoHeatLevel,
    int newDuration,
  ) async {
    try {
      final updatedLevel = autoHeatLevel.copyWith(duration: newDuration);

      if (userType == UserType.driver) {
        final updatedSettings = state.driverSettings.copyWith(
          autoHeatLevels: state.driverSettings.autoHeatLevels
              .map((level) => level == autoHeatLevel ? updatedLevel : level)
              .toList(),
        );
        emit(state.copyWith(driverSettings: updatedSettings, clearError: true));
      } else {
        final updatedSettings = state.passengerSettings.copyWith(
          autoHeatLevels: state.passengerSettings.autoHeatLevels
              .map((level) => level == autoHeatLevel ? updatedLevel : level)
              .toList(),
        );
        emit(state.copyWith(
            passengerSettings: updatedSettings, clearError: true));
      }

      await _settingsService.saveSettings(
        userType == UserType.driver
            ? state.driverSettings
            : state.passengerSettings,
        userType,
      );
    } catch (e) {
      emit(state.copyWith(error: e.toString()));
    }
  }

  Future<void> updateTemperatureThreshold(
    UserType userType,
    double temperature,
  ) async {
    try {
      if (userType == UserType.driver) {
        final updatedSettings = state.driverSettings.copyWith(
          temperatureThreshold: temperature,
        );
        emit(state.copyWith(driverSettings: updatedSettings, clearError: true));
        await _settingsService.saveSettings(updatedSettings, UserType.driver);
      } else {
        final updatedSettings = state.passengerSettings.copyWith(
          temperatureThreshold: temperature,
        );
        emit(state.copyWith(
            passengerSettings: updatedSettings, clearError: true));
        await _settingsService.saveSettings(
            updatedSettings, UserType.passenger);
      }
    } catch (e) {
      emit(state.copyWith(error: e.toString()));
    }
  }

  Future<void> resetToDefaults(UserType userType) async {
    try {
      final defaultSettings = ManualHeatSettings.defaultFor(userType);

      if (userType == UserType.driver) {
        emit(state.copyWith(driverSettings: defaultSettings, clearError: true));
      } else {
        emit(state.copyWith(
            passengerSettings: defaultSettings, clearError: true));
      }

      await _settingsService.saveSettings(defaultSettings, userType);
    } catch (e) {
      emit(state.copyWith(error: e.toString()));
    }
  }

  Future<void> applyPresetSettings(
    ManualHeatSettings driverSettings,
    ManualHeatSettings passengerSettings,
  ) async {
    try {
      emit(state.copyWith(
        driverSettings: driverSettings,
        passengerSettings: passengerSettings,
        clearError: true,
      ));

      await _settingsService.saveSettings(driverSettings, UserType.driver);
      await _settingsService.saveSettings(
          passengerSettings, UserType.passenger);
    } catch (e) {
      emit(state.copyWith(error: e.toString()));
    }
  }

  ManualHeatSettings _validateSettings(ManualHeatSettings settings) {
    final validatedLevels = settings.autoHeatLevels.map((level) {
      return AutoHeatLevel(
        duration: level.duration.clamp(0, 15),
        level: level.level,
      );
    }).toList();

    return settings.copyWith(autoHeatLevels: validatedLevels);
  }
}
