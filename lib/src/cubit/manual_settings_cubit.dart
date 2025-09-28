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
            passengerSettings: ManualHeatSettings.defaultFor(UserType.passenger),
          ),
        );

  Future<void> initialize() async {
    emit(state.copyWith(isLoading: true));

    try {
      final driverSettings = await _settingsService.getSettings(UserType.driver);
      final passengerSettings = await _settingsService.getSettings(UserType.passenger);

      final validatedDriverSettings = _validateSettings(driverSettings);
      final validatedPassengerSettings = _validateSettings(passengerSettings);

      emit(state.copyWith(
        driverSettings: validatedDriverSettings,
        passengerSettings: validatedPassengerSettings,
        isLoading: false,
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
        emit(state.copyWith(driverSettings: updatedSettings));
      } else {
        final updatedSettings = state.passengerSettings.copyWith(
          autoHeatLevels: state.passengerSettings.autoHeatLevels
              .map((level) => level == autoHeatLevel ? updatedLevel : level)
              .toList(),
        );
        emit(state.copyWith(passengerSettings: updatedSettings));
      }

      await _settingsService.saveSettings(
        userType == UserType.driver ? state.driverSettings : state.passengerSettings,
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
        emit(state.copyWith(driverSettings: updatedSettings));
        await _settingsService.saveSettings(updatedSettings, UserType.driver);
      } else {
        final updatedSettings = state.passengerSettings.copyWith(
          temperatureThreshold: temperature,
        );
        emit(state.copyWith(passengerSettings: updatedSettings));
        await _settingsService.saveSettings(updatedSettings, UserType.passenger);
      }
    } catch (e) {
      emit(state.copyWith(error: e.toString()));
    }
  }

  Future<void> resetToDefaults(UserType userType) async {
    try {
      final defaultSettings = ManualHeatSettings.defaultFor(userType);

      if (userType == UserType.driver) {
        emit(state.copyWith(driverSettings: defaultSettings));
      } else {
        emit(state.copyWith(passengerSettings: defaultSettings));
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
      ));

      // Сохраняем оба набора настроек
      await _settingsService.saveSettings(driverSettings, UserType.driver);
      await _settingsService.saveSettings(passengerSettings, UserType.passenger);
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
