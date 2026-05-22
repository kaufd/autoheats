// FILE: lib/src/cubit/mode_cubit.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Bloc-слой режима подогрева — синхронизирует ModeService (persistence),
//            HvacService (команды HVAC) и AutoHeatService (авторежим).
//   SCOPE: setMode, setHeatLevel, toggleHeatLevel, восстановление состояния,
//          публикация ModesState для UI.
//   DEPENDS: M-HVAC, M-AUTO-HEAT, M-ENUMS, M-LOGGER
//   LINKS: M-MODE, V-M-MODE, DF-SET-HEAT, DF-AUTO-HEAT
//   ROLE: RUNTIME
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   ModeCubit - Cubit<ModesState> поверх ModeService/HvacService/AutoHeatService
//   ModeCubit(ModeService, HvacService) - конструктор; запускает _initialize
//   _initialize - дефолты, подписка AutoHeatService, восстановление состояния
//   _getStateByUser - выбрать ModeState для UserType
//   _updateUserState - пересобрать и эмитить ModesState
//   getModeByUser - имя текущего HeatMode для UserType
//   getHeatLevelByUser - текущий уровень для UserType
//   setMode(UserType, String) - сменить режим, persist, управлять авторежимом + Logger marker
//   setHeatLevel(UserType, int) - persist + HvacService.setSeatHeatLevel + emit + Logger marker
//   toggleHeatLevel - циклический перебор уровня в режиме manual
//   cabinTemperature - геттер температуры из AutoHeatService
//   _manageAutoHeat - старт/стоп AutoHeatService по HeatMode
//   _initializeHeatModes - применить восстановленные режимы при старте
//   close - dispose AutoHeatService и закрытие Cubit
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.1.0 - Phase-3: добавлены Logger markers setMode/setHeatLevel]
//   PREVIOUS_CHANGE: [v0.2.0 - GRACE-инициализация: добавлены MODULE_CONTRACT и MODULE_MAP]
// END_CHANGE_SUMMARY

import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/services/mode_service.dart';
import 'package:autoheat/src/services/auto_heat_service.dart';
import 'package:autoheat/src/services/hvac_service.dart';
import 'package:autoheat/src/utils/logger.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import 'mode_state_cubit.dart';

class ModeCubit extends Cubit<ModesState> {
  final ModeService _modeService;
  final HvacService _hvacService;
  final AutoHeatService _autoHeatService = AutoHeatService();

  ModeCubit(this._modeService, this._hvacService)
      : super(ModesState(states: [
          ModeState(
              userType: UserType.driver,
              heatMode: HeatMode.manual,
              heatLevel: 0),
          ModeState(
              userType: UserType.passenger,
              heatMode: HeatMode.manual,
              heatLevel: 0),
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
    final index =
        updatedStates.indexWhere((state) => state.userType == userType);
    updatedStates[index] = newState;
    emit(ModesState(states: updatedStates));
  }

  String getModeByUser(UserType user) {
    return _getStateByUser(user).heatMode.name;
  }

  int getHeatLevelByUser(UserType user) {
    return _getStateByUser(user).heatLevel;
  }

  // START_CONTRACT: setMode
  //   PURPOSE: Сменить режим сиденья, сохранить и запустить/остановить авторежим.
  //   INPUTS: { userType: UserType, newMode: String HeatMode.name }
  //   OUTPUTS: { Future<void> }
  //   SIDE_EFFECTS: SharedPreferences, AutoHeatService, emit, Logger marker BLOCK_SET_MODE.
  //   LINKS: M-MODE, M-AUTO-HEAT, M-LOGGER, V-M-MODE, DF-AUTO-HEAT
  // END_CONTRACT: setMode
  Future<void> setMode(UserType userType, String newMode) async {
    // START_BLOCK_SET_MODE
    final heatMode = HeatModeExtension.fromString(newMode);
    await _modeService.setMode(userType, heatMode);

    final currentState = _getStateByUser(userType);
    final newHeatLevel =
        heatMode == HeatMode.manual ? 0 : currentState.heatLevel;

    _updateUserState(userType, mode: heatMode, heatLevel: newHeatLevel);
    _manageAutoHeat(userType, heatMode);
    Logger.info(
      'ModeCubit',
      'setMode',
      'BLOCK_SET_MODE',
      'applied',
      {'userType': userType.name, 'mode': heatMode.name},
    );
    // END_BLOCK_SET_MODE
  }

  // START_CONTRACT: setHeatLevel
  //   PURPOSE: Установить уровень подогрева, сохранить, отправить в HVAC и обновить state.
  //   INPUTS: { userType: UserType, level: int (0..3) }
  //   OUTPUTS: { Future<void> }
  //   SIDE_EFFECTS: SharedPreferences, HvacService, emit, Logger marker BLOCK_SET_HEAT_LEVEL.
  //   LINKS: M-MODE, M-HVAC, M-LOGGER, V-M-MODE, DF-SET-HEAT
  // END_CONTRACT: setHeatLevel
  Future<void> setHeatLevel(UserType userType, int level) async {
    // START_BLOCK_SET_HEAT_LEVEL
    await _modeService.setHeatLevel(userType, level);
    await _hvacService.setSeatHeatLevel(userType, level);
    _updateUserState(userType, heatLevel: level);
    Logger.info(
      'ModeCubit',
      'setHeatLevel',
      'BLOCK_SET_HEAT_LEVEL',
      'applied',
      {'userType': userType.name, 'level': level},
    );
    // END_BLOCK_SET_HEAT_LEVEL
  }

  void toggleHeatLevel(UserType userType) {
    final currentState = _getStateByUser(userType);

    if (currentState.heatMode == HeatMode.manual) {
      final newLevel =
          currentState.heatLevel == 3 ? 0 : currentState.heatLevel + 1;
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
