// FILE: test/unit/auto_heat_service_test.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Unit-тесты AutoHeatService — расписание авторежима через FakeAsync.
//   SCOPE: последовательности 3->2->1->0 по диапазонам, отмена при смене
//          температуры, stopAutoHeat, независимость UserType, idempotency,
//          поведение при неизвестной температуре, проброс через initialize,
//          initial read через HvacService seed, защита от sensor-noise restart.
//   DEPENDS: M-AUTO-HEAT, M-HVAC, M-CONSTANTS-TEMPERATURE, M-ENUMS, M-MANUAL-SETTINGS
//   LINKS: V-M-AUTO-HEAT, FA-005
//   ROLE: TEST
//   MAP_MODE: LOCALS
// END_MODULE_CONTRACT

import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/models/manual_settings.dart';
import 'package:autoheat/src/services/auto_heat_service.dart';
import 'package:fake_async/fake_async.dart';
import 'package:flutter_test/flutter_test.dart';

import '../_helpers/fake_hvac_service.dart';
import '../_helpers/logger_test_sink.dart';

void main() {
  late LoggerTestSink logs;
  // AutoHeatService — синглтон; tearDown вызывает dispose(), чтобы сбросить
  // listener/timer/current-temperature состояние между сценариями.

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

  // START_BLOCK_CUSTOM_SETTINGS
  test('scenario-10: custom settings durations drive 3->2->1->0 schedule', () {
    fakeAsync((async) {
      final captured = <int>[];
      final settings = ManualHeatSettings(
        autoHeatLevels: const [
          AutoHeatLevel(level: 1, duration: 1),
          AutoHeatLevel(level: 2, duration: 2),
          AutoHeatLevel(level: 3, duration: 3),
        ],
        temperatureThreshold: 5.0,
      );

      AutoHeatService().setTemperature(4.0);
      AutoHeatService()
          .startAutoHeat(UserType.driver, captured.add, settings: settings);

      expect(captured, [3]);
      async.elapse(const Duration(minutes: 3));
      expect(captured, [3, 2]);
      async.elapse(const Duration(minutes: 2));
      expect(captured, [3, 2, 1]);
      async.elapse(const Duration(minutes: 1));
      expect(captured, [3, 2, 1, 0]);
    });
  });

  test('scenario-11: custom threshold turns auto off at or above threshold',
      () {
    fakeAsync((async) {
      final captured = <int>[];
      final settings = ManualHeatSettings(
        autoHeatLevels: const [
          AutoHeatLevel(level: 1, duration: 1),
          AutoHeatLevel(level: 2, duration: 2),
          AutoHeatLevel(level: 3, duration: 3),
        ],
        temperatureThreshold: 5.0,
      );

      AutoHeatService().setTemperature(5.0);
      AutoHeatService()
          .startAutoHeat(UserType.driver, captured.add, settings: settings);

      expect(captured, [0]);
      async.elapse(const Duration(minutes: 10));
      expect(captured, [0]);
    });
  });
  // END_BLOCK_CUSTOM_SETTINGS

  test(
      'scenario-12: initial HVAC read seeds auto schedule without sensor event',
      () {
    fakeAsync((async) {
      final fakeHvac = FakeHvacService()..programmedTemperature = -3.0;
      final captured = <int>[];

      AutoHeatService().initialize(fakeHvac);
      AutoHeatService().seedCurrentTemperatureFromHvac();
      async.flushMicrotasks();

      AutoHeatService().startAutoHeat(UserType.driver, captured.add);

      expect(captured, [3]);
      expect(fakeHvac.getCabinTemperatureCallCount, 1);
    });
  });

  // START_BLOCK_SENSOR_NOISE_GUARD
  test(
      'scenario-13: repeated same fallback range events do not restart sequence',
      () {
    fakeAsync((async) {
      final fakeHvac = FakeHvacService();
      final captured = <int>[];

      AutoHeatService().initialize(fakeHvac);
      fakeHvac.emitTemperature(-3.0);
      AutoHeatService().startAutoHeat(UserType.driver, captured.add);
      expect(captured, [3]);

      async.elapse(const Duration(minutes: 3));
      fakeHvac.emitTemperature(-3.2);
      fakeHvac.emitTemperature(-2.9);
      expect(captured, [3],
          reason: 'same cold plan must not callback(3) again');

      async.elapse(const Duration(minutes: 3));
      expect(captured, [3, 2], reason: 'original 6m timer was not reset');
      async.elapse(const Duration(minutes: 4));
      expect(captured, [3, 2, 1]);
      async.elapse(const Duration(minutes: 10));
      expect(captured, [3, 2, 1, 0]);
      expect(async.nonPeriodicTimerCount, 0);
    });
  });

  test('scenario-14: different fallback sequence restarts once', () {
    fakeAsync((async) {
      final fakeHvac = FakeHvacService();
      final captured = <int>[];

      AutoHeatService().initialize(fakeHvac);
      fakeHvac.emitTemperature(-3.0); // cold: 6/4/10
      AutoHeatService().startAutoHeat(UserType.driver, captured.add);
      expect(captured, [3]);

      async.elapse(const Duration(minutes: 3));
      fakeHvac.emitTemperature(7.0); // warm: 2/2/6
      expect(captured, [3, 3]);

      async.elapse(const Duration(minutes: 2));
      expect(captured, [3, 3, 2]);
      async.elapse(const Duration(minutes: 2));
      expect(captured, [3, 3, 2, 1]);
      async.elapse(const Duration(minutes: 6));
      expect(captured, [3, 3, 2, 1, 0]);
      expect(async.nonPeriodicTimerCount, 0);
    });
  });

  test('scenario-15: repeated off events emit callback zero once', () {
    fakeAsync((async) {
      final fakeHvac = FakeHvacService();
      final captured = <int>[];

      AutoHeatService().initialize(fakeHvac);
      fakeHvac.emitTemperature(-3.0);
      AutoHeatService().startAutoHeat(UserType.driver, captured.add);
      expect(captured, [3]);

      fakeHvac.emitTemperature(12.0);
      expect(captured, [3, 0]);
      fakeHvac.emitTemperature(13.0);
      fakeHvac.emitTemperature(14.0);
      expect(captured, [3, 0]);
      async.elapse(const Duration(minutes: 30));
      expect(captured, [3, 0]);
      expect(async.nonPeriodicTimerCount, 0);
    });
  });

  test(
      'scenario-16: explicit repeated startAutoHeat still restarts from level 3',
      () {
    fakeAsync((async) {
      final captured = <int>[];
      AutoHeatService().setTemperature(-3.0);
      AutoHeatService().startAutoHeat(UserType.driver, captured.add);
      expect(captured, [3]);
      async.elapse(const Duration(minutes: 3));

      AutoHeatService().startAutoHeat(UserType.driver, captured.add);

      expect(captured, [3, 3]);
      async.elapse(const Duration(minutes: 6));
      expect(captured, [3, 3, 2]);
    });
  });

  test(
      'scenario-17: custom settings repeated below-threshold events do not restart',
      () {
    fakeAsync((async) {
      final fakeHvac = FakeHvacService();
      final captured = <int>[];
      final settings = ManualHeatSettings(
        autoHeatLevels: const [
          AutoHeatLevel(level: 1, duration: 1),
          AutoHeatLevel(level: 2, duration: 2),
          AutoHeatLevel(level: 3, duration: 3),
        ],
        temperatureThreshold: 5.0,
      );

      AutoHeatService().initialize(fakeHvac);
      fakeHvac.emitTemperature(4.0);
      AutoHeatService()
          .startAutoHeat(UserType.driver, captured.add, settings: settings);
      expect(captured, [3]);

      async.elapse(const Duration(minutes: 1));
      fakeHvac.emitTemperature(3.5);
      fakeHvac.emitTemperature(4.5);
      expect(captured, [3]);

      async.elapse(const Duration(minutes: 2));
      expect(captured, [3, 2]);
      async.elapse(const Duration(minutes: 2));
      expect(captured, [3, 2, 1]);
      async.elapse(const Duration(minutes: 1));
      expect(captured, [3, 2, 1, 0]);
    });
  });
  // END_BLOCK_SENSOR_NOISE_GUARD

  // START_BLOCK_HVAC_WIRING
  test(
      'initialize(hvac): emitTemperature через cabin listener '
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
