// FILE: lib/src/services/auto_heat_service.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Реализация авторежима — по температуре салона запускает расписание
//            уровней подогрева 3->2->1->0 индивидуально для driver/passenger.
//   SCOPE: подписка на onCabinTemperatureChanged, per-UserType Timer-каскады,
//          startAutoHeat/stopAutoHeat, ручная setTemperature для тестов.
//   DEPENDS: M-HVAC, M-ENUMS, M-CONSTANTS-TEMPERATURE, M-LOGGER
//   LINKS: M-AUTO-HEAT, V-M-AUTO-HEAT, DF-AUTO-HEAT
//   ROLE: RUNTIME
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   AutoHeatService - синглтон авторежима
//   AutoHeatService() - factory, возвращает единственный _instance
//   initialize(HvacService) - подписка на onCabinTemperatureChanged
//   setTemperature(double) - ручная установка температуры (тесты/диагностика)
//   currentTemperature - геттер последней известной температуры салона
//   startAutoHeat(UserType, callback) - регистрация колбэка уровня и старт расписания
//   stopAutoHeat(UserType) - отмена Timer'а и удаление колбэка для UserType
//   _updateAutoHeatForAllUsers - пересчёт расписания для всех активных UserType
//   _updateAutoHeatForUser - отмена старого Timer'а и старт нового расписания
//   _startHeatSequence - callback(3) и Logger marker BLOCK_START_HEAT_SEQUENCE
//   _scheduleNextLevel - Timer-переходы 2->1->0 + marker BLOCK_SCHEDULE_NEXT_LEVEL
//   _getSequence - HeatSequence для текущей температуры (null при range.off)
//   dispose - отмена всех Timer'ов и очистка колбэков
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.1.0 - Phase-3: добавлены Logger markers для авто-расписания]
//   PREVIOUS_CHANGE: [v0.2.0 - GRACE-инициализация: добавлены MODULE_CONTRACT и MODULE_MAP]
// END_CHANGE_SUMMARY

import 'dart:async';
import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/constants/temperature_constants.dart';
import 'package:autoheat/src/services/hvac_service.dart';
import 'package:autoheat/src/utils/logger.dart';

class AutoHeatService {
  static final AutoHeatService _instance = AutoHeatService._internal();
  factory AutoHeatService() => _instance;
  AutoHeatService._internal();

  HvacService? _hvacService;
  double? _currentTemperature;

  final Map<UserType, Timer?> _heatTimers = {};

  final Map<UserType, Function(int)> _heatLevelCallbacks = {};

  void initialize(HvacService hvacService) {
    _hvacService = hvacService;
    _hvacService!.onCabinTemperatureChanged = (double temperature) {
      _currentTemperature = temperature;
      _updateAutoHeatForAllUsers();
    };
  }

  void setTemperature(double celsius) {
    _currentTemperature = celsius;
    _updateAutoHeatForAllUsers();
  }

  double? get currentTemperature => _currentTemperature;

  void startAutoHeat(UserType userType, Function(int) onHeatLevelChanged) {
    _heatLevelCallbacks[userType] = onHeatLevelChanged;
    _updateAutoHeatForUser(userType);
  }

  // START_CONTRACT: stopAutoHeat
  //   PURPOSE: Остановить авторежим конкретного сиденья и удалить callback.
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
    Logger.info(
      'AutoHeatService',
      'stopAutoHeat',
      'BLOCK_STOP',
      'stopped',
      {'userType': userType.name},
    );
    // END_BLOCK_STOP
  }

  void _updateAutoHeatForAllUsers() {
    for (final userType in _heatLevelCallbacks.keys) {
      _updateAutoHeatForUser(userType);
    }
  }

  void _updateAutoHeatForUser(UserType userType) {
    if (_currentTemperature == null) return;

    final callback = _heatLevelCallbacks[userType];
    if (callback == null) return;

    _heatTimers[userType]?.cancel();

    final sequence = TemperatureConstants.getHeatSequence(_currentTemperature!);
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
        _scheduleNextLevel(userType, 1, _getSequence()?.level2Duration ?? 0);
      } else if (nextLevel == 1) {
        callback(1);
        _scheduleNextLevel(userType, 0, _getSequence()?.level1Duration ?? 0);
      } else {
        callback(0);
      }
    });
    // END_BLOCK_SCHEDULE_NEXT_LEVEL
  }

  HeatSequence? _getSequence() {
    if (_currentTemperature == null) return null;
    return TemperatureConstants.getHeatSequence(_currentTemperature!);
  }

  void dispose() {
    for (final timer in _heatTimers.values) {
      timer?.cancel();
    }
    _heatTimers.clear();
    _heatLevelCallbacks.clear();
  }
}
