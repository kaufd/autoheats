// FILE: lib/src/services/auto_heat_service.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Реализация авторежима — по температуре салона запускает расписание
//            уровней подогрева 3->2->1->0 индивидуально для driver/passenger.
//   SCOPE: подписка на HvacService cabin-temperature multi-listener,
//          initial temperature seed, effective-plan guard от sensor noise,
//          per-UserType Timer-каскады, startAutoHeat/stopAutoHeat,
//          optional ManualHeatSettings runtime sequence.
//   DEPENDS: M-HVAC, M-ENUMS, M-CONSTANTS-TEMPERATURE, M-MANUAL-SETTINGS, M-LOGGER
//   LINKS: M-AUTO-HEAT, V-M-AUTO-HEAT, DF-AUTO-HEAT, DF-INIT-TEMP, FA-002, FA-003, FA-005
//   ROLE: RUNTIME
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   AutoHeatService - синглтон авторежима
//   AutoHeatService() - factory, возвращает единственный _instance
//   initialize(HvacService) - подписка на cabin-temperature listener API
//   seedCurrentTemperatureFromHvac - initial read через HvacService.getCabinTemperature
//   setTemperature(double) - ручная установка температуры (тесты/диагностика)
//   currentTemperature - геттер последней известной температуры салона
//   startAutoHeat(UserType, callback, settings?) - регистрация callback + optional custom settings
//   stopAutoHeat(UserType) - отмена Timer'а и удаление callback/settings для UserType
//   _handleCabinTemperature - listener target, cache + passive пересчёт активных users
//   _updateAutoHeatForAllUsers - пересчёт расписания для всех активных UserType
//   _updateAutoHeatForUser - effective-plan guard, отмена Timer'а и старт нового расписания
//   _planKeyFor - stable key: off или sequence durations для sensor-noise guard
//   _startHeatSequence - callback(3) и Logger marker BLOCK_START_HEAT_SEQUENCE
//   _scheduleNextLevel - Timer-переходы 2->1->0 + marker BLOCK_SCHEDULE_NEXT_LEVEL
//   _getSequence - custom ManualHeatSettings sequence или TemperatureConstants fallback
//   dispose - отписка listener, отмена Timer'ов и очистка состояния
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.4.0 - Phase-4 Slice-4: effective-plan guard от sensor noise]
//   PREVIOUS_CHANGE: [v1.3.0 - Phase-4 Slice-3: подписка на HvacService multi-listener и initial seed]
// END_CHANGE_SUMMARY

import 'dart:async';
import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/constants/temperature_constants.dart';
import 'package:autoheat/src/models/manual_settings.dart';
import 'package:autoheat/src/services/hvac_service.dart';
import 'package:autoheat/src/utils/logger.dart';

class AutoHeatService {
  static final AutoHeatService _instance = AutoHeatService._internal();
  factory AutoHeatService() => _instance;
  AutoHeatService._internal();

  HvacService? _hvacService;
  CabinTemperatureListener? _temperatureListener;
  double? _currentTemperature;

  final Map<UserType, Timer?> _heatTimers = {};
  final Map<UserType, Function(int)> _heatLevelCallbacks = {};
  final Map<UserType, ManualHeatSettings> _manualSettingsByUser = {};
  final Map<UserType, String> _activePlanKeys = {};

  void initialize(HvacService hvacService) {
    final previousHvacService = _hvacService;
    final previousListener = _temperatureListener;
    if (previousHvacService != null && previousListener != null) {
      previousHvacService.removeCabinTemperatureListener(previousListener);
    }

    _hvacService = hvacService;
    _temperatureListener = _handleCabinTemperature;
    _hvacService!.addCabinTemperatureListener(
      _temperatureListener!,
      emitCurrent: true,
    );
  }

  // START_CONTRACT: seedCurrentTemperatureFromHvac
  //   PURPOSE: Засидить текущую температуру из HvacService initial read без ожидания sensor event.
  //   INPUTS: none
  //   OUTPUTS: { Future<void> }
  //   SIDE_EFFECTS: HvacService.getCabinTemperature публикует температуру listener'ам.
  //   LINKS: M-AUTO-HEAT, M-HVAC, V-M-AUTO-HEAT, DF-INIT-TEMP, FA-003
  // END_CONTRACT: seedCurrentTemperatureFromHvac
  Future<void> seedCurrentTemperatureFromHvac() async {
    await _hvacService?.getCabinTemperature();
  }

  void setTemperature(double celsius) {
    _handleCabinTemperature(celsius);
  }

  double? get currentTemperature => _currentTemperature;

  void _handleCabinTemperature(double temperature) {
    _currentTemperature = temperature;
    _updateAutoHeatForAllUsers(allowSamePlanRestart: false);
  }

  // START_CONTRACT: startAutoHeat
  //   PURPOSE: Зарегистрировать callback авторежима и стартовать расписание.
  //   INPUTS: { userType, onHeatLevelChanged, settings? }
  //   OUTPUTS: { void }
  //   SIDE_EFFECTS: Может вызвать callback сразу и создать Timer.
  //   LINKS: M-AUTO-HEAT, M-MANUAL-SETTINGS, V-M-AUTO-HEAT, FA-002
  // END_CONTRACT: startAutoHeat
  void startAutoHeat(
    UserType userType,
    Function(int) onHeatLevelChanged, {
    ManualHeatSettings? settings,
  }) {
    _heatLevelCallbacks[userType] = onHeatLevelChanged;
    if (settings != null) {
      _manualSettingsByUser[userType] = settings;
    } else {
      _manualSettingsByUser.remove(userType);
    }
    _activePlanKeys.remove(userType);
    _updateAutoHeatForUser(userType, allowSamePlanRestart: true);
  }

  // START_CONTRACT: stopAutoHeat
  //   PURPOSE: Остановить авторежим конкретного сиденья и удалить callback/settings.
  //   INPUTS: { userType: UserType }
  //   OUTPUTS: { void }
  //   SIDE_EFFECTS: Отмена Timer, Logger marker BLOCK_STOP.
  //   LINKS: M-AUTO-HEAT, M-LOGGER, V-M-AUTO-HEAT
  // END_CONTRACT: stopAutoHeat
  void stopAutoHeat(UserType userType) {
    // START_BLOCK_STOP
    _heatTimers[userType]?.cancel();
    _heatTimers[userType] = null;
    _heatLevelCallbacks.remove(userType);
    _manualSettingsByUser.remove(userType);
    _activePlanKeys.remove(userType);
    Logger.info(
      'AutoHeatService',
      'stopAutoHeat',
      'BLOCK_STOP',
      'stopped',
      {'userType': userType.name},
    );
    // END_BLOCK_STOP
  }

  void _updateAutoHeatForAllUsers({required bool allowSamePlanRestart}) {
    for (final userType in _heatLevelCallbacks.keys) {
      _updateAutoHeatForUser(
        userType,
        allowSamePlanRestart: allowSamePlanRestart,
      );
    }
  }

  void _updateAutoHeatForUser(
    UserType userType, {
    required bool allowSamePlanRestart,
  }) {
    if (_currentTemperature == null) return;

    final callback = _heatLevelCallbacks[userType];
    if (callback == null) return;

    final sequence = _getSequence(userType);
    final planKey = _planKeyFor(sequence);
    if (!allowSamePlanRestart && _activePlanKeys[userType] == planKey) {
      return;
    }

    _activePlanKeys[userType] = planKey;
    _heatTimers[userType]?.cancel();

    if (sequence == null) {
      callback(0);
      return;
    }

    _startHeatSequence(userType, sequence);
  }

  // START_CONTRACT: _startHeatSequence
  //   PURPOSE: Начать расписание с уровня 3 и запланировать переход на 2.
  //   INPUTS: { userType: UserType, sequence: HeatSequence }
  //   OUTPUTS: { void }
  //   SIDE_EFFECTS: callback(3), Logger marker BLOCK_START_HEAT_SEQUENCE.
  //   LINKS: M-AUTO-HEAT, M-CONSTANTS-TEMPERATURE, M-LOGGER, V-M-AUTO-HEAT
  // END_CONTRACT: _startHeatSequence
  void _startHeatSequence(UserType userType, HeatSequence sequence) {
    // START_BLOCK_START_HEAT_SEQUENCE
    final callback = _heatLevelCallbacks[userType];
    if (callback == null) return;

    Logger.info(
      'AutoHeatService',
      'startHeatSequence',
      'BLOCK_START_HEAT_SEQUENCE',
      'started',
      {'userType': userType.name, 'level': 3},
    );
    callback(3);
    _scheduleNextLevel(userType, 2, sequence.level3Duration);
    // END_BLOCK_START_HEAT_SEQUENCE
  }

  // START_CONTRACT: _scheduleNextLevel
  //   PURPOSE: Запланировать следующий переход уровня авторежима.
  //   INPUTS: { userType: UserType, nextLevel: int, currentDuration: int minutes }
  //   OUTPUTS: { void }
  //   SIDE_EFFECTS: Создаёт Timer, Logger marker BLOCK_SCHEDULE_NEXT_LEVEL.
  //   LINKS: M-AUTO-HEAT, M-CONSTANTS-TEMPERATURE, M-LOGGER, V-M-AUTO-HEAT
  // END_CONTRACT: _scheduleNextLevel
  void _scheduleNextLevel(
      UserType userType, int nextLevel, int currentDuration) {
    // START_BLOCK_SCHEDULE_NEXT_LEVEL
    if (currentDuration == 0) return;

    Logger.info(
      'AutoHeatService',
      'scheduleNextLevel',
      'BLOCK_SCHEDULE_NEXT_LEVEL',
      'scheduled',
      {
        'userType': userType.name,
        'level': nextLevel,
        'duration': currentDuration
      },
    );
    _heatTimers[userType] = Timer(Duration(minutes: currentDuration), () {
      final callback = _heatLevelCallbacks[userType];
      if (callback == null) return;

      if (nextLevel == 2) {
        callback(2);
        _scheduleNextLevel(
            userType, 1, _getSequence(userType)?.level2Duration ?? 0);
      } else if (nextLevel == 1) {
        callback(1);
        _scheduleNextLevel(
            userType, 0, _getSequence(userType)?.level1Duration ?? 0);
      } else {
        callback(0);
      }
    });
    // END_BLOCK_SCHEDULE_NEXT_LEVEL
  }

  String _planKeyFor(HeatSequence? sequence) {
    if (sequence == null) return 'off';
    return 'sequence:${sequence.level3Duration},${sequence.level2Duration},${sequence.level1Duration}';
  }

  HeatSequence? _getSequence(UserType userType) {
    if (_currentTemperature == null) return null;

    final settings = _manualSettingsByUser[userType];
    if (settings == null) {
      return TemperatureConstants.getHeatSequence(_currentTemperature!);
    }

    if (_currentTemperature! >= settings.temperatureThreshold) return null;

    int durationFor(int level) {
      for (final autoHeatLevel in settings.autoHeatLevels) {
        if (autoHeatLevel.level == level) {
          return autoHeatLevel.duration.clamp(0, 15);
        }
      }
      return 0;
    }

    return HeatSequence(
      level3Duration: durationFor(3),
      level2Duration: durationFor(2),
      level1Duration: durationFor(1),
    );
  }

  void dispose() {
    final hvacService = _hvacService;
    final listener = _temperatureListener;
    if (hvacService != null && listener != null) {
      hvacService.removeCabinTemperatureListener(listener);
    }

    for (final timer in _heatTimers.values) {
      timer?.cancel();
    }
    _heatTimers.clear();
    _heatLevelCallbacks.clear();
    _manualSettingsByUser.clear();
    _activePlanKeys.clear();
    _hvacService = null;
    _temperatureListener = null;
    _currentTemperature = null;
  }
}
