// FILE: lib/src/cubit/mode_cubit.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Bloc-слой режима подогрева — синхронизирует ModeService (persistence),
//            HvacService (команды HVAC) и AutoHeatService (авторежим).
//   SCOPE: ModeCubit над ModesState; setMode/applyPreset/toggleHeatLevel/setHeatLevel,
//          source-of-settings routing для AutoHeatService: auto→TemperatureConstants,
//          presets→preset.settings, manual→stop.
//   DEPENDS: M-HVAC, M-AUTO-HEAT, M-ENUMS, M-LOGGER, M-PRESET
//   LINKS: M-MODE, V-M-MODE, DF-SET-HEAT, DF-AUTO-HEAT, DF-PRESET-APPLY, DF-INIT-TEMP
//   ROLE: RUNTIME
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   ModeCubit - Cubit<ModesState> поверх ModeService/HvacService/PresetService
//   ModeCubit(ModeService, HvacService, PresetService) - конструктор; запускает _initialize
//   _initialize - дефолты, подписка AutoHeatService, initial temperature seed, восстановление состояния
//   _getStateByUser - выбрать ModeState для UserType
//   _updateUserState - пересобрать и эмитить ModesState
//   _persistAndApplyHeatLevel - persist + HvacService + state update без публичного лога
//   getModeByUser - имя текущего HeatMode для UserType
//   getHeatLevelByUser - текущий уровень для UserType
//   setMode(UserType, String, {ManualHeatSettings? settings}) - сменить режим, persist, управлять авторежимом + Logger marker
//   setHeatLevel(UserType, int) - persist + HvacService.setSeatHeatLevel + emit + Logger marker
//   applyPreset(Preset) - применить пресет: presets mode + preset.settings → AutoHeatService
//   toggleHeatLevel - последовательный перебор уровня; non-manual -> manual level 1
//   _startAutoHeat - передать settings в AutoHeatService (null → TemperatureConstants fallback)
//   _manageAutoHeat - старт/стоп AutoHeatService по HeatMode + settings
//   _initializeHeatModes - применить восстановленные режимы при старте (presets через PresetService)
//   _resolveSelectedPresetSettings - lookup active preset settings via PresetService
//   close - dispose AutoHeatService и закрытие Cubit
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.3.0 - Mode-source decoupling: drop ManualSettingsService, источник settings приходит из caller или PresetService]
//   PREVIOUS_CHANGE: [v1.5.0 - Phase-4 Slice-9: ModeCubit синхронизирует selected preset id]
// END_CHANGE_SUMMARY

import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/models/manual_settings.dart';
import 'package:autoheat/src/models/preset.dart';
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
  final PresetService _presetService;
  final AutoHeatService _autoHeatService = AutoHeatService();

  ModeCubit(
    this._modeService,
    this._hvacService,
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
  //   PURPOSE: Сменить режим сиденья, persist и запустить/остановить авторежим.
  //   INPUTS: { userType: UserType, newMode: String HeatMode.name, settings?: ManualHeatSettings }
  //   OUTPUTS: { Future<void> }
  //   SIDE_EFFECTS: SharedPreferences, AutoHeatService, emit, Logger marker BLOCK_SET_MODE.
  //                 НЕ пишет selectedPresetId — это owned by PresetCubit.
  //   LINKS: M-MODE, M-AUTO-HEAT, M-LOGGER, V-M-MODE, DF-AUTO-HEAT
  // END_CONTRACT: setMode
  Future<void> setMode(
    UserType userType,
    String newMode, {
    ManualHeatSettings? settings,
  }) async {
    // START_BLOCK_SET_MODE
    final heatMode = HeatModeExtension.fromString(newMode);
    final currentState = _getStateByUser(userType);

    await _modeService.setMode(userType, heatMode);
    _updateUserState(userType, mode: heatMode);

    await _manageAutoHeat(userType, heatMode, settings: settings);

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
  //   PURPOSE: Применить user preset как presets-режим с его settings.
  //   INPUTS: { preset: Preset }
  //   OUTPUTS: { Future<void> }
  //   SIDE_EFFECTS: SharedPreferences (mode), AutoHeatService start с preset.settings, emit.
  //                 НЕ пишет selectedPresetId — это owned by PresetCubit (caller).
  //   LINKS: M-MODE, M-PRESET, M-AUTO-HEAT, M-LOGGER, V-M-MODE, V-M-PRESET, DF-PRESET-APPLY
  // END_CONTRACT: applyPreset
  Future<void> applyPreset(Preset preset) async {
    // START_BLOCK_APPLY_PRESET
    await setMode(
      preset.userType,
      HeatMode.presets.name,
      settings: preset.settings,
    );

    Logger.info(
      'ModeCubit',
      'applyPreset',
      'BLOCK_APPLY_PRESET',
      'applied',
      {
        'userType': preset.userType.name,
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
      _autoHeatService.stopAutoHeat(userType);
      _updateUserState(userType, mode: HeatMode.manual);
      await setHeatLevel(userType, 1);
    }
  }

  Future<void> _startAutoHeat(
    UserType userType, {
    required ManualHeatSettings? settings,
  }) async {
    Future<void>? immediateHeatLevelFuture;

    void handleAutoHeatLevel(int newLevel) {
      final future = setHeatLevel(userType, newLevel);
      immediateHeatLevelFuture ??= future;
    }

    _autoHeatService.startAutoHeat(
      userType,
      handleAutoHeatLevel,
      settings: settings, // null → AutoHeatService fallback to TemperatureConstants
    );

    await immediateHeatLevelFuture;
  }

  Future<void> _manageAutoHeat(
    UserType userType,
    HeatMode heatMode, {
    ManualHeatSettings? settings,
  }) async {
    switch (heatMode) {
      case HeatMode.manual:
        _autoHeatService.stopAutoHeat(userType);
        return;
      case HeatMode.auto:
        await _startAutoHeat(userType, settings: null);
        return;
      case HeatMode.presets:
        if (settings == null) {
          // Defensive: UI не должна слать setMode(presets) без settings.
          // Логируем и не меняем алгоритм-state.
          Logger.warn(
            'ModeCubit',
            '_manageAutoHeat',
            'BLOCK_MANAGE_AUTO_HEAT',
            'presets-without-settings (defensive no-op)',
            {'userType': userType.name},
          );
          return;
        }
        await _startAutoHeat(userType, settings: settings);
        return;
    }
  }

  Future<void> _initializeHeatModes(List<ModeState> states) async {
    for (final state in states) {
      switch (state.heatMode) {
        case HeatMode.manual:
          if (state.heatLevel > 0) {
            // ignore: discarded_futures
            setHeatLevel(state.userType, state.heatLevel);
          }
          break;
        case HeatMode.auto:
          await _startAutoHeat(state.userType, settings: null);
          break;
        case HeatMode.presets:
          final presetSettings =
              await _resolveSelectedPresetSettings(state.userType);
          if (presetSettings == null) {
            // Нет валидного preset — откат на manual+0.
            await _modeService.setMode(state.userType, HeatMode.manual);
            _updateUserState(state.userType,
                mode: HeatMode.manual, heatLevel: 0);
            await _modeService.setHeatLevel(state.userType, 0);
          } else {
            await _startAutoHeat(state.userType, settings: presetSettings);
          }
          break;
      }
    }
  }

  Future<ManualHeatSettings?> _resolveSelectedPresetSettings(
      UserType userType) async {
    final selectedId = await _presetService.getSelectedPresetId(userType);
    if (selectedId == null) return null;
    final presets = await _presetService.getPresets(userType);
    for (final p in presets) {
      if (p.id == selectedId) return p.settings;
    }
    return null;
  }

  @override
  Future<void> close() {
    _autoHeatService.dispose();
    return super.close();
  }
}
