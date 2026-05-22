// FILE: test/unit/auto_heat_service_test.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Unit-тесты AutoHeatService — расписание авторежима через FakeAsync.
//   SCOPE: последовательности 3->2->1->0 по диапазонам, отмена при смене
//          температуры, stopAutoHeat, независимость UserType, idempotency,
//          поведение при неизвестной температуре, проброс через initialize.
//   DEPENDS: M-AUTO-HEAT, M-HVAC, M-CONSTANTS-TEMPERATURE, M-ENUMS
//   LINKS: V-M-AUTO-HEAT
//   ROLE: TEST
//   MAP_MODE: LOCALS
// END_MODULE_CONTRACT

import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/services/auto_heat_service.dart';
import 'package:fake_async/fake_async.dart';
import 'package:flutter_test/flutter_test.dart';

import '../_helpers/fake_hvac_service.dart';
import '../_helpers/logger_test_sink.dart';

void main() {
  late LoggerTestSink logs;
  // AutoHeatService — синглтон. _currentTemperature нельзя сбросить в null
  // публичным API (dispose() чистит только таймеры и колбэки). Поэтому тест
  // "неизвестная температура" обязан идти ПЕРВЫМ — пока ни один тест ещё не
  // вызвал setTemperature/emitTemperature в этом изоляте.

  setUp(() {
    logs = LoggerTestSink();
  });

  tearDown(() {
    AutoHeatService().dispose();
    logs.dispose();
  });

  // START_BLOCK_NULL_TEMPERATURE
  test('scenario-9: startAutoHeat при неизвестной температуре — без callback',
      () {
    fakeAsync((async) {
      final captured = <int>[];
      AutoHeatService().startAutoHeat(UserType.driver, captured.add);
      async.elapse(const Duration(minutes: 60));
      expect(captured, isEmpty);
      expect(async.nonPeriodicTimerCount, 0);
    });
  });
  // END_BLOCK_NULL_TEMPERATURE

  // START_BLOCK_FULL_SEQUENCES
  group('Полные последовательности расписания', () {
    test('scenario-1: cold (-3C) -> [3,2,1,0] по 6/4/10 мин', () {
      fakeAsync((async) {
        final captured = <int>[];
        AutoHeatService().setTemperature(-3.0);
        AutoHeatService().startAutoHeat(UserType.driver, captured.add);
        expect(captured, [3]);
        async.elapse(const Duration(minutes: 6));
        expect(captured, [3, 2]);
        async.elapse(const Duration(minutes: 4));
        expect(captured, [3, 2, 1]);
        async.elapse(const Duration(minutes: 10));
        expect(captured, [3, 2, 1, 0]);
        expect(async.nonPeriodicTimerCount, 0);
        expect(
          logs.lines,
          contains(
              '[AutoHeatService][startHeatSequence][BLOCK_START_HEAT_SEQUENCE] started | userType=driver, level=3'),
        );
        expect(
          logs.lines,
          contains(
              '[AutoHeatService][scheduleNextLevel][BLOCK_SCHEDULE_NEXT_LEVEL] scheduled | userType=driver, level=2, duration=6'),
        );
        // forbidden-4: каждый уровень ровно один раз
        expect(captured.toSet().length, 4);
      });
    });

    test('scenario-2: warm (7C) -> [3,2,1,0] по 2/2/6 мин', () {
      fakeAsync((async) {
        final captured = <int>[];
        AutoHeatService().setTemperature(7.0);
        AutoHeatService().startAutoHeat(UserType.driver, captured.add);
        async.elapse(const Duration(minutes: 2));
        async.elapse(const Duration(minutes: 2));
        async.elapse(const Duration(minutes: 6));
        expect(captured, [3, 2, 1, 0]);
        expect(async.nonPeriodicTimerCount, 0);
      });
    });

    test('scenario-3: extreme (-15C) -> [3,2,1,0] по 10/8/15 мин', () {
      fakeAsync((async) {
        final captured = <int>[];
        AutoHeatService().setTemperature(-15.0);
        AutoHeatService().startAutoHeat(UserType.driver, captured.add);
        async.elapse(const Duration(minutes: 10));
        async.elapse(const Duration(minutes: 8));
        async.elapse(const Duration(minutes: 15));
        expect(captured, [3, 2, 1, 0]);
        expect(async.nonPeriodicTimerCount, 0);
      });
    });
  });
  // END_BLOCK_FULL_SEQUENCES

  // START_BLOCK_CANCELLATION
  group('Отмена и остановка', () {
    test('scenario-4: смена температуры в off отменяет Timer -> [3,0]', () {
      fakeAsync((async) {
        final captured = <int>[];
        AutoHeatService().setTemperature(-3.0);
        AutoHeatService().startAutoHeat(UserType.driver, captured.add);
        expect(captured, [3]);
        async.elapse(const Duration(minutes: 3));
        AutoHeatService().setTemperature(12.0);
        expect(captured, [3, 0]);
        async.elapse(const Duration(minutes: 30));
        expect(captured, [3, 0]);
        expect(async.nonPeriodicTimerCount, 0);
      });
    });

    test('scenario-5: stopAutoHeat останавливает расписание -> [3]', () {
      fakeAsync((async) {
        final captured = <int>[];
        AutoHeatService().setTemperature(-3.0);
        AutoHeatService().startAutoHeat(UserType.driver, captured.add);
        expect(captured, [3]);
        AutoHeatService().stopAutoHeat(UserType.driver);
        async.elapse(const Duration(minutes: 30));
        expect(captured, [3]);
        expect(async.nonPeriodicTimerCount, 0);
        expect(
          logs.lines,
          contains(
              '[AutoHeatService][stopAutoHeat][BLOCK_STOP] stopped | userType=driver'),
        );
      });
    });
  });
  // END_BLOCK_CANCELLATION

  // START_BLOCK_INDEPENDENCE
  test('scenario-6: stopAutoHeat одного UserType не трогает другой', () {
    fakeAsync((async) {
      final driver = <int>[];
      final passenger = <int>[];
      AutoHeatService().setTemperature(-3.0);
      AutoHeatService().startAutoHeat(UserType.driver, driver.add);
      AutoHeatService().startAutoHeat(UserType.passenger, passenger.add);
      expect(driver, [3]);
      expect(passenger, [3]);
      AutoHeatService().stopAutoHeat(UserType.driver);
      async.elapse(const Duration(minutes: 20));
      expect(driver, [3], reason: 'driver остановлен');
      expect(passenger, [3, 2, 1, 0], reason: 'passenger продолжил');
      expect(async.nonPeriodicTimerCount, 0);
    });
  });
  // END_BLOCK_INDEPENDENCE

  // START_BLOCK_IDEMPOTENCY
  test('scenario-7: повторный startAutoHeat перезапускает с уровня 3', () {
    fakeAsync((async) {
      final captured = <int>[];
      AutoHeatService().setTemperature(-3.0);
      AutoHeatService().startAutoHeat(UserType.driver, captured.add);
      expect(captured, [3]);
      async.elapse(const Duration(minutes: 3));
      AutoHeatService().startAutoHeat(UserType.driver, captured.add);
      expect(captured, [3, 3], reason: 'перезапуск с уровня 3');
      async.elapse(const Duration(minutes: 20));
      expect(captured, [3, 3, 2, 1, 0]);
      expect(async.nonPeriodicTimerCount, 0);
    });
  });
  // END_BLOCK_IDEMPOTENCY

  // START_BLOCK_BOUNDARY
  test('scenario-8: setTemperature(10.0) (range off) -> callback(0)', () {
    fakeAsync((async) {
      final captured = <int>[];
      AutoHeatService().setTemperature(10.0);
      AutoHeatService().startAutoHeat(UserType.driver, captured.add);
      expect(captured, [0]);
      async.elapse(const Duration(minutes: 30));
      expect(async.nonPeriodicTimerCount, 0);
    });
  });
  // END_BLOCK_BOUNDARY

  // START_BLOCK_HVAC_WIRING
  test(
      'initialize(hvac): emitTemperature через onCabinTemperatureChanged '
      'запускает расписание', () {
    fakeAsync((async) {
      final fakeHvac = FakeHvacService();
      final captured = <int>[];
      AutoHeatService().initialize(fakeHvac);
      fakeHvac.emitTemperature(50.0); // известное off-состояние
      AutoHeatService().startAutoHeat(UserType.driver, captured.add);
      captured.clear(); // отбросить стартовый шум (cb(0) на temp=50)
      fakeHvac.emitTemperature(-3.0); // реальное событие датчика
      expect(captured, [3]);
      async.elapse(const Duration(minutes: 20));
      expect(captured, [3, 2, 1, 0]);
      expect(async.nonPeriodicTimerCount, 0);
    });
  });
  // END_BLOCK_HVAC_WIRING
}
