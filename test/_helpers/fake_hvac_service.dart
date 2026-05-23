// FILE: test/_helpers/fake_hvac_service.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Ручной фейк HvacService для unit-тестов ModeCubit, AutoHeatService и CabinTemperatureCubit.
//   SCOPE: Запись вызовов setSeatHeatLevel, программируемая температура салона,
//          multi-listener инжекция события температуры через emitTemperature.
//   DEPENDS: M-HVAC, M-ENUMS, M-CABIN-TEMPERATURE
//   LINKS: V-M-HVAC, V-M-AUTO-HEAT, V-M-MODE, V-M-CABIN-TEMPERATURE
//   ROLE: TEST
//   MAP_MODE: LOCALS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   FakeHvacService - фейк HvacService с записью вызовов и инжекцией температуры
//   recordedSetSeatHeatCalls - список (userType, level) для assert
//   programmedTemperature - значение, возвращаемое getCabinTemperature
//   getCabinTemperatureCallCount - счётчик initial-read вызовов
//   add/removeCabinTemperatureListener - multi-listener API как у HvacService
//   lastCabinTemperature - последняя опубликованная температура салона
//   emitTemperature - опубликовать событие температуры всем listener'ам
// END_MODULE_MAP

import 'package:android_automotive_plugin/android_automotive_plugin.dart';
import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/services/hvac_service.dart';

class FakeHvacService implements HvacService {
  final Set<CabinTemperatureListener> _cabinTemperatureListeners = {};
  double? _lastCabinTemperature;

  /// Значение, возвращаемое [getCabinTemperature]. По умолчанию совпадает с
  /// fallback реального HvacService (20.0 °C).
  double programmedTemperature = 20.0;

  /// Все вызовы [setSeatHeatLevel] в порядке поступления — для проверок в тестах.
  final List<({UserType userType, int level})> recordedSetSeatHeatCalls = [];

  /// Сколько раз вызван [initialize] — для проверки идемпотентности у потребителей.
  int initializeCallCount = 0;

  /// Сколько раз вызван [getCabinTemperature] — для проверки initial-read paths.
  int getCabinTemperatureCallCount = 0;

  bool _initialized = false;

  /// Имитировать событие датчика температуры салона: как если бы плагин вызвал
  /// onHvacChangeEvent для InOutCAR_INSIDE и HvacService пробросил его дальше.
  void emitTemperature(double celsius) {
    _publishCabinTemperature(celsius);
  }

  @override
  Future<void> initialize() async {
    initializeCallCount++;
    _initialized = true;
  }

  @override
  bool get isInitialized => _initialized;

  @override
  Future<void> setSeatHeatLevel(UserType userType, int level) async {
    recordedSetSeatHeatCalls.add((userType: userType, level: level));
  }

  @override
  Future<double> getCabinTemperature() async {
    getCabinTemperatureCallCount++;
    _publishCabinTemperature(programmedTemperature);
    return programmedTemperature;
  }

  @override
  double? get lastCabinTemperature => _lastCabinTemperature;

  @override
  void addCabinTemperatureListener(
    CabinTemperatureListener listener, {
    bool emitCurrent = false,
  }) {
    _cabinTemperatureListeners.add(listener);
    final current = _lastCabinTemperature;
    if (emitCurrent && current != null) {
      listener(current);
    }
  }

  @override
  void removeCabinTemperatureListener(CabinTemperatureListener listener) {
    _cabinTemperatureListeners.remove(listener);
  }

  void _publishCabinTemperature(double celsius) {
    _lastCabinTemperature = celsius;
    for (final listener
        in List<CabinTemperatureListener>.of(_cabinTemperatureListeners)) {
      listener(celsius);
    }
  }

  @override
  void dispose() {
    _cabinTemperatureListeners.clear();
    _lastCabinTemperature = null;
    _initialized = false;
  }

  /// Потребители фейка (ModeCubit, AutoHeatService) к плагину напрямую не
  /// обращаются — доступ через фейк намеренно не поддержан (fail-fast).
  @override
  AndroidAutomotivePlugin get androidAutomotivePlugin =>
      throw UnimplementedError(
          'FakeHvacService не предоставляет AndroidAutomotivePlugin');
}
