// FILE: lib/src/services/auto_heat_service.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Реализация авторежима — по температуре салона запускает расписание
//            уровней подогрева с адаптивным step-down по температуре, холодным
//            стартом и безопасным таймером-максимумом.
//   SCOPE: подписка на HvacService cabin-temperature multi-listener,
//          initial temperature seed, per-UserType адаптивные переходы 3→2→1→0,
//          startAutoHeat/stopAutoHeat, optional ManualHeatSettings runtime sequence.
//   DEPENDS: M-HVAC, M-ENUMS, M-CONSTANTS-TEMPERATURE
//   LINKS: M-AUTO-HEAT, V-M-AUTO-HEAT, DF-AUTO-HEAT, DF-INIT-TEMP
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
//   _handleCabinTemperature - listener target, cache + пересчёт активных users
//   _updateAutoHeatForAllUsers - пересчёт для всех активных UserType
//   _updateAutoHeatForUser - адаптивный step-down: temperature-driven + max-timer safety
//   _stepDownLevel - снизить уровень, callback + schedule max-timer для следующего
//   _temperatureBasedLevel - вычислить целевой уровень по текущей температуре
//   _durationForLevel - длительность уровня из ManualHeatSettings или HeatSequence
//   _stepDownThresholdFor - порог step-down для уровня
//   _getSequence - custom ManualHeatSettings sequence или TemperatureConstants fallback
//   dispose - отписка listener, отмена Timer'ов и очистка состояния
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v2.0.0 - Adaptive auto-heat: temperature-driven step-down + cold-start + no level-3 restart on warming]
//   PREVIOUS_CHANGE: [v1.4.0 - Phase-4 Slice-4: effective-plan guard от sensor noise]
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

  /// Текущий уровень подогрева (3/2/1/0). null = неактивен.
  final Map<UserType, int> _activeLevels = {};

  /// Температура салона на момент вызова startAutoHeat (для cold-start detection).
  final Map<UserType, double> _startTemperatures = {};

  /// Для каких пользователей callback(0) уже был отправлен при off-состоянии.
  /// Сбрасывается при первом старте — чтобы избежать дублирования callback(0)
  /// при повторных событиях датчика в off-диапазоне.
  final Set<UserType> _offCallbackSent = {};

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
  //   LINKS: M-AUTO-HEAT, M-HVAC, V-M-AUTO-HEAT, DF-INIT-TEMP
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
    _updateAutoHeatForAllUsers();
  }

  // START_CONTRACT: startAutoHeat
  //   PURPOSE: Зарегистрировать callback авторежима и стартовать расписание.
  //   INPUTS: { userType, onHeatLevelChanged, settings? }
  //   OUTPUTS: { void }
  //   SIDE_EFFECTS: Может вызвать callback сразу и создать Timer.
  //   LINKS: M-AUTO-HEAT, V-M-AUTO-HEAT
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
    _activeLevels.remove(userType);
    _startTemperatures.remove(userType);
    _offCallbackSent.remove(userType);
    _updateAutoHeatForUser(userType);
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
    _activeLevels.remove(userType);
    _startTemperatures.remove(userType);
    _offCallbackSent.remove(userType);
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

  /// Главный метод: принимает решение о смене уровня по температуре.
  ///
  /// - Если не активен — старт с уровня 3, запись startTemperature.
  /// - Если активен — проверка: не пора ли step-down по температуре?
  /// - Max-duration timer как safety net: если температура не достигла
  ///   порога step-down за отведённое время, таймер форсирует переход.
  /// - При пересечении границы диапазона вверх НЕ перезапускает уровень 3 —
  ///   продолжает с текущего уровня, просто меняя дельты.
  void _updateAutoHeatForUser(UserType userType) {
    if (_currentTemperature == null) return;

    final callback = _heatLevelCallbacks[userType];
    if (callback == null) return;

    // Для пресетов, которые уже запущены: фиксированное расписание до конца.
    // Порог temperatureThreshold влияет только на старт, но не прерывает каскад.
    final isPresetRunning = _manualSettingsByUser.containsKey(userType) &&
        _activeLevels[userType] != null;
    if (isPresetRunning) return;

    final sequence = _getSequence(userType);

    // Выше порога авторежима — выключить
    if (sequence == null) {
      _heatTimers[userType]?.cancel();
      _activeLevels.remove(userType);
      _startTemperatures.remove(userType);
      if (!_offCallbackSent.contains(userType)) {
        _offCallbackSent.add(userType);
        callback(0);
      }
      return;
    }

    final activeLevel = _activeLevels[userType];

    // Первый старт, перезапуск после stop, или re-entry из off-состояния
    if (activeLevel == null || activeLevel == 0) {
      _offCallbackSent.remove(userType);
      _startTemperatures[userType] = _currentTemperature!;
      _stepDownLevel(userType, 3, sequence, callback);
      return;
    }

    // Авто-режим: адаптивный step-down по температуре.
    final temp = _currentTemperature!;

    // Если температура ушла в диапазон, где естественный уровень ниже текущего —
    // шагаем вниз сразу до целевого уровня (не по одному)
    final tempBasedLevel = _temperatureBasedLevel(temp, sequence);
    if (tempBasedLevel < activeLevel) {
      _stepDownLevel(userType, tempBasedLevel, sequence, callback);
      return;
    }

    final stepDownAt = _stepDownThresholdFor(activeLevel, sequence);

    if (temp >= stepDownAt) {
      // Достигнут порог — шагаем вниз
      final nextLevel = activeLevel - 1;
      _stepDownLevel(userType, nextLevel, sequence, callback);
    }
    // Если temp < stepDownAt — остаёмся на текущем уровне.
    // Max-duration timer сработает сам, если температура не поднимется вовремя.
  }

  /// Снизить уровень, вызвать callback и запланировать max-timer для следующего шага.
  void _stepDownLevel(
    UserType userType,
    int newLevel,
    HeatSequence sequence,
    Function(int) callback,
  ) {
    _activeLevels[userType] = newLevel;

    Logger.info(
      'AutoHeatService',
      'stepDownLevel',
      'BLOCK_STEP_DOWN',
      'level changed',
      {
        'userType': userType.name,
        'level': newLevel,
        'temperature': _currentTemperature,
      },
    );

    callback(newLevel);

    if (newLevel <= 0) {
      _startTemperatures.remove(userType);
      return;
    }

    // Запланировать max-duration timer для перехода на следующий уровень.
    // Таймер — safety net: если температура не достигнет порога step-down,
    // уровень всё равно снизится по истечении максимального времени.
    final duration = _durationForLevel(newLevel, sequence);
    _scheduleMaxTimer(userType, newLevel - 1, duration, sequence);
  }

  void _scheduleMaxTimer(
    UserType userType,
    int nextLevel,
    int maxDurationMinutes,
    HeatSequence sequence,
  ) {
    _heatTimers[userType]?.cancel();
    if (maxDurationMinutes <= 0) return;

    Logger.info(
      'AutoHeatService',
      'scheduleMaxTimer',
      'BLOCK_SCHEDULE_MAX_TIMER',
      'scheduled',
      {
        'userType': userType.name,
        'nextLevel': nextLevel,
        'maxMinutes': maxDurationMinutes,
      },
    );

    _heatTimers[userType] = Timer(Duration(minutes: maxDurationMinutes), () {
      final callback = _heatLevelCallbacks[userType];
      if (callback == null) return;

      // Для пресетов: каскад должен дойти до конца независимо от температуры.
      // _getSequence для пресета может вернуть null, если t° >= threshold,
      // но running-пресет не должен прерываться.
      final isPreset = _manualSettingsByUser.containsKey(userType);
      if (!isPreset) {
        final seq = _getSequence(userType);
        if (seq == null) {
          callback(0);
          return;
        }
        _stepDownLevel(userType, nextLevel, seq, callback);
        return;
      }

      final seq = _getSequence(userType);
      if (seq == null) {
        // Порог превышен на старте (первый тик) — завершаем.
        // Но если пресет уже был запущен, _activeLevels != null и _getSequence
        // не вызывается из _updateAutoHeatForUser. Здесь мы в max-timer колбэке.
        // Для безопасности: если seq == null (t° >= threshold), но пресет активен —
        // продолжаем каскад с дефолтными длительностями из сохранённых settings.
        final settings = _manualSettingsByUser[userType]!;
        int dur(int level) {
          for (final l in settings.autoHeatLevels) {
            if (l.level == level) return l.duration.clamp(0, 15);
          }
          return 0;
        }
        final fallback = HeatSequence(
          level3Duration: dur(3),
          level2Duration: dur(2),
          level1Duration: dur(1),
        );
        _stepDownLevel(userType, nextLevel, fallback, callback);
        return;
      }

      Logger.info(
        'AutoHeatService',
        'scheduleMaxTimer',
        'BLOCK_MAX_TIMER_FIRED',
        'max duration reached, stepping down',
        {
          'userType': userType.name,
          'nextLevel': nextLevel,
        },
      );

      _stepDownLevel(userType, nextLevel, seq, callback);
    });
  }

  /// Определить естественный уровень по температуре в данном sequence.
  int _temperatureBasedLevel(double temp, HeatSequence sequence) {
    if (temp >= sequence.level1StepDownCelsius) return 0;
    if (temp >= sequence.level2StepDownCelsius) return 1;
    if (temp >= sequence.level3StepDownCelsius) return 2;
    return 3;
  }

  int _durationForLevel(int level, HeatSequence sequence) {
    switch (level) {
      case 3:
        return sequence.level3Duration;
      case 2:
        return sequence.level2Duration;
      case 1:
        return sequence.level1Duration;
      default:
        return 0;
    }
  }

  double _stepDownThresholdFor(int level, HeatSequence sequence) {
    switch (level) {
      case 3:
        return sequence.level3StepDownCelsius;
      case 2:
        return sequence.level2StepDownCelsius;
      case 1:
        return sequence.level1StepDownCelsius;
      default:
        return double.infinity;
    }
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
    _activeLevels.clear();
    _startTemperatures.clear();
    _offCallbackSent.clear();
    _hvacService = null;
    _temperatureListener = null;
    _currentTemperature = null;
  }
}
