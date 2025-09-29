import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/services/mode_service.dart';
import 'package:autoheat/src/services/auto_heat_service.dart';
import 'package:autoheat/src/services/seat_heat_service.dart';
import 'package:autoheat/src/services/temperature_sensor_service.dart';
import 'package:autoheat/src/services/temperature_event_service.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import 'mode_state_cubit.dart';

class ModeCubit extends Cubit<ModesState> {
  final ModeService _modeService;
  final SeatHeatService _seatHeatService;
  final TemperatureSensorService _temperatureSensorService;
  final TemperatureEventService _temperatureEventService;
  final AutoHeatService _autoHeatService = AutoHeatService();

  ModeCubit(this._modeService, this._seatHeatService, this._temperatureSensorService,
      this._temperatureEventService)
      : super(ModesState(states: [
          ModeState(userType: UserType.driver, heatMode: HeatMode.manual, heatLevel: 0),
          ModeState(userType: UserType.passenger, heatMode: HeatMode.manual, heatLevel: 0),
        ])) {
    _initialize();
  }

  void _initialize() async {
    await _modeService.initializeDefaults();

    _autoHeatService.initialize(_temperatureSensorService, _temperatureEventService);

    final modes = _modeService.getAllModes();

    final states = modes.entries
        .map((entry) => ModeState(
              userType: entry.key,
              heatMode: entry.value['mode'] as HeatMode,
              heatLevel: entry.value['heatLevel'] as int,
            ))
        .toList();

    emit(ModesState(states: states));

    _initializeHeatModes(states);
  }

  String getModeByUser(UserType user) {
    return state.states.firstWhere((mode) => mode.userType == user).heatMode.name;
  }

  void setMode(UserType userType, String newMode) async {
    final HeatMode heatMode = HeatModeExtension.fromString(newMode);
    await _modeService.setMode(userType, heatMode);

    final updatedStates = state.states.toList();
    final index = updatedStates.indexWhere((state) => state.userType == userType);
    final currentState = updatedStates[index];

    final newHeatLevel = heatMode == HeatMode.manual ? 0 : currentState.heatLevel;

    updatedStates[index] = ModeState(
      userType: userType,
      heatMode: heatMode,
      heatLevel: newHeatLevel,
    );

    _manageAutoHeat(userType, heatMode);

    emit(ModesState(states: updatedStates));
  }

  int getHeatLevelByUser(UserType user) {
    return state.states.firstWhere((mode) => mode.userType == user).heatLevel;
  }

  void setHeatLevel(UserType userType, int level) async {
    await _modeService.setHeatLevel(userType, level);

    await _seatHeatService.setSeatHeatLevel(userType, level);

    final updatedStates = state.states.toList();
    final index = updatedStates.indexWhere((state) => state.userType == userType);
    final currentState = updatedStates[index];
    updatedStates[index] = ModeState(
      userType: userType,
      heatMode: currentState.heatMode,
      heatLevel: level,
    );

    emit(ModesState(states: updatedStates));
  }

  void toggleHeatLevel(UserType userType) {
    final currentMode = state.states.firstWhere((state) => state.userType == userType).heatMode;

    if (currentMode == HeatMode.manual) {
      final currentLevel = getHeatLevelByUser(userType);
      final newLevel = currentLevel == 3 ? 0 : currentLevel + 1;
      setHeatLevel(userType, newLevel);
    } else {
      setMode(userType, HeatMode.manual.name);
      setHeatLevel(userType, 1);
    }
  }

  void _manageAutoHeat(UserType userType, HeatMode heatMode) {
    if (heatMode == HeatMode.auto) {
      _autoHeatService.startAutoHeat(userType, (newLevel) {
        setHeatLevel(userType, newLevel);
      });
    } else {
      _autoHeatService.stopAutoHeat(userType);
    }
  }

  double? getCabinTemperature() {
    return _autoHeatService.currentTemperature?.celsius;
  }

  void _initializeHeatModes(List<ModeState> states) {
    for (final state in states) {
      if (state.heatMode == HeatMode.auto) {
        _autoHeatService.startAutoHeat(state.userType, (newLevel) {
          setHeatLevel(state.userType, newLevel);
        });
      } else if (state.heatMode == HeatMode.manual && state.heatLevel > 0) {
        setHeatLevel(state.userType, state.heatLevel);
      }
    }
  }

  @override
  Future<void> close() {
    _autoHeatService.dispose();
    return super.close();
  }
}
