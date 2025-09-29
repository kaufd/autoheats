import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/services/mode_service.dart';
import 'package:autoheat/src/services/auto_heat_service.dart';
import 'package:autoheat/src/services/hvac_service.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import 'mode_state_cubit.dart';

class ModeCubit extends Cubit<ModesState> {
  final ModeService _modeService;
  final HvacService _hvacService;
  final AutoHeatService _autoHeatService = AutoHeatService();

  ModeCubit(this._modeService, this._hvacService)
      : super(ModesState(states: [
          ModeState(userType: UserType.driver, heatMode: HeatMode.manual, heatLevel: 0),
          ModeState(userType: UserType.passenger, heatMode: HeatMode.manual, heatLevel: 0),
        ])) {
    _initialize();
  }

  Future<void> _initialize() async {
    await _modeService.initializeDefaults();
    _autoHeatService.initialize(_hvacService);

    final states = _modeService
        .getAllModes()
        .entries
        .map((entry) => ModeState(
              userType: entry.key,
              heatMode: entry.value['mode'] as HeatMode,
              heatLevel: entry.value['heatLevel'] as int,
            ))
        .toList();

    emit(ModesState(states: states));
    _initializeHeatModes(states);
  }

  ModeState _getStateByUser(UserType userType) {
    return state.states.firstWhere((mode) => mode.userType == userType);
  }

  void _updateUserState(UserType userType, {HeatMode? mode, int? heatLevel}) {
    final currentState = _getStateByUser(userType);
    final newState = ModeState(
      userType: userType,
      heatMode: mode ?? currentState.heatMode,
      heatLevel: heatLevel ?? currentState.heatLevel,
    );

    final updatedStates = state.states.toList();
    final index = updatedStates.indexWhere((state) => state.userType == userType);
    updatedStates[index] = newState;
    emit(ModesState(states: updatedStates));
  }

  String getModeByUser(UserType user) {
    return _getStateByUser(user).heatMode.name;
  }

  int getHeatLevelByUser(UserType user) {
    return _getStateByUser(user).heatLevel;
  }

  Future<void> setMode(UserType userType, String newMode) async {
    final heatMode = HeatModeExtension.fromString(newMode);
    await _modeService.setMode(userType, heatMode);

    final currentState = _getStateByUser(userType);
    final newHeatLevel = heatMode == HeatMode.manual ? 0 : currentState.heatLevel;

    _updateUserState(userType, mode: heatMode, heatLevel: newHeatLevel);
    _manageAutoHeat(userType, heatMode);
  }

  Future<void> setHeatLevel(UserType userType, int level) async {
    await _modeService.setHeatLevel(userType, level);
    await _hvacService.setSeatHeatLevel(userType, level);
    _updateUserState(userType, heatLevel: level);
  }

  void toggleHeatLevel(UserType userType) {
    final currentState = _getStateByUser(userType);

    if (currentState.heatMode == HeatMode.manual) {
      final newLevel = currentState.heatLevel == 3 ? 0 : currentState.heatLevel + 1;
      setHeatLevel(userType, newLevel);
    } else {
      setMode(userType, HeatMode.manual.name);
      setHeatLevel(userType, 1);
    }
  }

  double? get cabinTemperature => _autoHeatService.currentTemperature;

  void _manageAutoHeat(UserType userType, HeatMode heatMode) {
    if (heatMode == HeatMode.auto) {
      _autoHeatService.startAutoHeat(userType, (newLevel) {
        setHeatLevel(userType, newLevel);
      });
    } else {
      _autoHeatService.stopAutoHeat(userType);
    }
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
