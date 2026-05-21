// FILE: test/_helpers/fake_hvac_service.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Ручной фейк HvacService для unit-тестов ModeCubit и AutoHeatService.
//   SCOPE: Запись вызовов setSeatHeatLevel, программируемая температура салона,
//          ручная инжекция события температуры через emitTemperature.
//   DEPENDS: M-HVAC, M-ENUMS
//   LINKS: V-M-HVAC, V-M-AUTO-HEAT, V-M-MODE
//   ROLE: TEST
//   MAP_MODE: LOCALS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   FakeHvacService - фейк HvacService с записью вызовов и инжекцией температуры
//   recordedSetSeatHeatCalls - список (userType, level) для assert
//   programmedTemperature - значение, возвращаемое getCabinTemperature
//   emitTemperature - дёрнуть onCabinTemperatureChanged как реальное событие датчика
// END_MODULE_MAP

import 'package:android_automotive_plugin/android_automotive_plugin.dart';
import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/services/hvac_service.dart';

class FakeHvacService implements HvacService {
  @override
  Function(double)? onCabinTemperatureChanged;

  /// Значение, возвращаемое [getCabinTemperature]. По умолчанию совпадает с
  /// fallback реального HvacService (20.0 °C).
  double programmedTemperature = 20.0;

  /// Все вызовы [setSeatHeatLevel] в порядке поступления — для проверок в тестах.
  final List<({UserType userType, int level})> recordedSetSeatHeatCalls = [];

  /// Сколько раз вызван [initialize] — для проверки идемпотентности у потребителей.
  int initializeCallCount = 0;

  bool _initialized = false;

  /// Имитировать событие датчика температуры салона: как если бы плагин вызвал
  /// onHvacChangeEvent для InOutCAR_INSIDE и HvacService пробросил его дальше.
  void emitTemperature(double celsius) {
    onCabinTemperatureChanged?.call(celsius);
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
  Future<double> getCabinTemperature() async => programmedTemperature;

  @override
  void dispose() {
    onCabinTemperatureChanged = null;
    _initialized = false;
  }

  /// Потребители фейка (ModeCubit, AutoHeatService) к плагину напрямую не
  /// обращаются — доступ через фейк намеренно не поддержан (fail-fast).
  @override
  AndroidAutomotivePlugin get androidAutomotivePlugin =>
      throw UnimplementedError(
          'FakeHvacService не предоставляет AndroidAutomotivePlugin');
}
