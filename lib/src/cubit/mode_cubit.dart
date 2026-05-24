// FILE: lib/src/cubit/mode_cubit.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Bloc-слой режима подогрева — синхронизирует ModeService (persistence),
//            HvacService (команды HVAC) и AutoHeatService (авторежим).
//   SCOPE: setMode, setHeatLevel, applyPreset, toggleHeatLevel,
//          восстановление состояния, initial temperature seed для AutoHeatService,
//          публикация ModesState для UI, selected preset id sync.
//   DEPENDS: M-HVAC, M-AUTO-HEAT, M-ENUMS, M-LOGGER, M-PRESET, M-MANUAL-SETTINGS
//   LINKS: M-MODE, V-M-MODE, DF-SET-HEAT, DF-AUTO-HEAT, DF-PRESET-APPLY, DF-INIT-TEMP
//   ROLE: RUNTIME
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   ModeCubit - Cubit<ModesState> поверх ModeService/HvacService/AutoHeatService
//   ModeCubit(ModeService, HvacService, ManualSettingsService, PresetService) - конструктор; запускает _initialize
//   _initialize - дефолты, подписка AutoHeatService, initial temperature seed, восстановление состояния
//   _getStateByUser - выбрать ModeState для UserType
//   _updateUserState - пересобрать и эмитить ModesState
//   _persistAndApplyHeatLevel - persist + HvacService + state update без публичного лога
//   getModeByUser - имя текущего HeatMode для UserType
//   getHeatLevelByUser - текущий уровень для UserType
//   setMode(UserType, String) - сменить режим, persist, управлять авторежимом + Logger marker; non-presets чистит selected preset
//   setHeatLevel(UserType, int) - persist + HvacService.setSeatHeatLevel + emit + Logger marker
//   applyPreset(Preset) - применить сохранённый mode/level пресета к persistence + HVAC + selected preset
//   toggleHeatLevel - последовательный перебор уровня; non-manual -> manual level 1
//   _startAutoHeat - загрузить ManualSettingsService settings и стартовать AutoHeatService
//   _manageAutoHeat - старт/стоп AutoHeatService по HeatMode
//   _initializeHeatModes - применить восстановленные режимы при старте
//   close - dispose AutoHeatService и закрытие Cubit
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.5.0 - Phase-4 Slice-9: ModeCubit синхронизирует selected preset id]
//   PREVIOUS_CHANGE: [v1.4.0 - Phase-4 Slice-3: initial temperature seed для AutoHeatService]
// END_CHANGE_SUMMARY

import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/models/preset.dart';
import 'package:autoheat/src/services/manual_settings_service.dart';
import 'package:autoheat/src/services/mode_service.dart';
import 'package:autoheat/src/services/preset_service.dart';
import 'package:autoheat/src/services/auto_heat_service.dart';
import 'package:autoheat/src/services/hvac_service.dart';
import 'package:autoheat/src/utils/logger.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import 'mode_state_cubit.dart';

class ModeCubit extends Cubit<ModesState> {
  final ModeService _modeService;
  final HvacService _hvacService;
  final ManualSettingsService _manualSettingsService;
  final PresetService _presetService;
  final AutoHeatService _autoHeatService = AutoHeatService();

  ModeCubit(
    this._modeService,
    this._hvacService,
    this._manualSettingsService,
    this._presetService,
  ) : super(ModesState(states: [
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
    await _autoHeatService.seedCurrentTemperatureFromHvac();

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
    await _initializeHeatModes(states);
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

  Future<void> _persistAndApplyHeatLevel(UserType userType, int level) async {
    await _modeService.setHeatLevel(userType, level);
    await _hvacService.setSeatHeatLevel(userType, level);
    _updateUserState(userType, heatLevel: level);
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
  //   SIDE_EFFECTS: SharedPreferences, AutoHeatService, selected preset id,
  //                 emit, Logger marker BLOCK_SET_MODE.
  //   LINKS: M-MODE, M-AUTO-HEAT, M-PRESET, M-LOGGER, V-M-MODE, DF-AUTO-HEAT, FA-011
  // END_CONTRACT: setMode
  Future<void> setMode(UserType userType, String newMode) async {
    // START_BLOCK_SET_MODE
    final heatMode = HeatModeExtension.fromString(newMode);
    final currentState = _getStateByUser(userType);

    await _modeService.setMode(userType, heatMode);
    if (heatMode != HeatMode.presets) {
      await _presetService.clearSelectedPresetId(userType);
    }
    _updateUserState(userType, mode: heatMode);

    await _manageAutoHeat(userType, heatMode);

    if (heatMode == HeatMode.manual && currentState.heatLevel != 0) {
      await _persistAndApplyHeatLevel(userType, 0);
    } else if (heatMode == HeatMode.manual) {
      _updateUserState(userType, heatLevel: 0);
    }

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
    await _persistAndApplyHeatLevel(userType, level);
    Logger.info(
      'ModeCubit',
      'setHeatLevel',
      'BLOCK_SET_HEAT_LEVEL',
      'applied',
      {'userType': userType.name, 'level': level},
    );
    // END_BLOCK_SET_HEAT_LEVEL
  }

  // START_CONTRACT: applyPreset
  //   PURPOSE: Применить сохранённый runtime mode/level пресета к конкретному сиденью.
  //   INPUTS: { preset: Preset }
  //   OUTPUTS: { Future<void> }
  //   SIDE_EFFECTS: SharedPreferences, AutoHeatService, HvacService,
  //                 selected preset id, emit, Logger marker BLOCK_APPLY_PRESET.
  //   LINKS: M-MODE, M-PRESET, M-HVAC, M-LOGGER, V-M-MODE, V-M-PRESET, DF-PRESET-APPLY
  // END_CONTRACT: applyPreset
  Future<void> applyPreset(Preset preset) async {
    // START_BLOCK_APPLY_PRESET
    final userType = preset.userType;
    final heatMode = HeatMode.presets;
    final heatLevel = 0;

    await _manageAutoHeat(userType, heatMode);

    await _modeService.setMode(userType, heatMode);
    await _presetService.setSelectedPresetId(userType, preset.id);
    _updateUserState(userType, mode: heatMode);
    await _persistAndApplyHeatLevel(userType, heatLevel);

    Logger.info(
      'ModeCubit',
      'applyPreset',
      'BLOCK_APPLY_PRESET',
      'applied',
      {
        'userType': userType.name,
        'mode': heatMode.name,
        'level': heatLevel,
        'presetId': preset.id,
      },
    );
    // END_BLOCK_APPLY_PRESET
  }

  Future<void> toggleHeatLevel(UserType userType) async {
    final currentState = _getStateByUser(userType);

    if (currentState.heatMode == HeatMode.manual) {
      final newLevel =
          currentState.heatLevel == 3 ? 0 : currentState.heatLevel + 1;
      await setHeatLevel(userType, newLevel);
    } else {
      await _modeService.setMode(userType, HeatMode.manual);
      await _presetService.clearSelectedPresetId(userType);
      _autoHeatService.stopAutoHeat(userType);
      _updateUserState(userType, mode: HeatMode.manual);
      await setHeatLevel(userType, 1);
    }
  }

  Future<void> _startAutoHeat(UserType userType) async {
    final settings = await _manualSettingsService.getSettings(userType);
    Future<void>? immediateHeatLevelFuture;

    void handleAutoHeatLevel(int newLevel) {
      final future = setHeatLevel(userType, newLevel);
      immediateHeatLevelFuture ??= future;
    }

    _autoHeatService.startAutoHeat(
      userType,
      handleAutoHeatLevel,
      settings: settings,
    );

    await immediateHeatLevelFuture;
  }

  Future<void> _manageAutoHeat(UserType userType, HeatMode heatMode) async {
    if (heatMode == HeatMode.auto) {
      await _startAutoHeat(userType);
    } else {
      _autoHeatService.stopAutoHeat(userType);
    }
  }

  Future<void> _initializeHeatModes(List<ModeState> states) async {
    for (final state in states) {
      if (state.heatMode == HeatMode.auto) {
        await _startAutoHeat(state.userType);
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
