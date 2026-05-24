// FILE: test/unit/auto_heat_service_test.dart
// VERSION: 2.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Unit-тесты AutoHeatService — адаптивный step-down по температуре + max-timer safety.
//   SCOPE: последовательности 3->2->1->0 через max-timer (если температура не достигает
//          порога step-down), отмена при off-температуре, stopAutoHeat, независимость
//          UserType, idempotency restart, custom settings, initial seed, холодный старт.
//   DEPENDS: M-AUTO-HEAT, M-HVAC, M-CONSTANTS-TEMPERATURE, M-ENUMS
//   LINKS: V-M-AUTO-HEAT
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
  group('Полные последовательности расписания (max-timer, без step-down по t°)', () {
    test('scenario-1: cold (-3C) -> [3,2,1,0] по max-timer 8/5/7 мин', () {
      fakeAsync((async) {
        final captured = <int>[];
        // -3°C в диапазоне cold. Step-down thresholds: -2/3/7.
        // Temp -3°C: level3 threshold=-2°C не достигнут → max-timer сработает.
        AutoHeatService().setTemperature(-3.0);
        AutoHeatService().startAutoHeat(UserType.driver, captured.add);
        expect(captured, [3]);
        async.elapse(const Duration(minutes: 8));
        expect(captured, [3, 2]);
        async.elapse(const Duration(minutes: 5));
        expect(captured, [3, 2, 1]);
        async.elapse(const Duration(minutes: 7));
        expect(captured, [3, 2, 1, 0]);
        expect(async.nonPeriodicTimerCount, 0);
        expect(captured.toSet().length, 4);
      });
    });

    test('scenario-2: warm (7C) -> [3,2,1,0] по max-timer 3/2/5 мин', () {
      fakeAsync((async) {
        final captured = <int>[];
        AutoHeatService().setTemperature(7.0);
        AutoHeatService().startAutoHeat(UserType.driver, captured.add);
        async.elapse(const Duration(minutes: 3));
        async.elapse(const Duration(minutes: 2));
        async.elapse(const Duration(minutes: 5));
        expect(captured, [3, 2, 1, 0]);
        expect(async.nonPeriodicTimerCount, 0);
      });
    });

    test('scenario-3: extreme (-15C) -> [3,2,1,0] по max-timer 15/10/8 мин', () {
      fakeAsync((async) {
        final captured = <int>[];
        AutoHeatService().setTemperature(-15.0);
        AutoHeatService().startAutoHeat(UserType.driver, captured.add);
        async.elapse(const Duration(minutes: 15));
        async.elapse(const Duration(minutes: 10));
        async.elapse(const Duration(minutes: 8));
        expect(captured, [3, 2, 1, 0]);
        expect(async.nonPeriodicTimerCount, 0);
      });
    });
  });
  // END_BLOCK_FULL_SEQUENCES

  // START_BLOCK_ADAPTIVE_STEPDOWN
  group('Адаптивный step-down по температуре', () {
    test('scenario-18: cold (-3C) — нагрев → досрочный step-down 3→2, затем 2→1', () {
      fakeAsync((async) {
        final captured = <int>[];
        AutoHeatService().setTemperature(-3.0);
        AutoHeatService().startAutoHeat(UserType.driver, captured.add);
        expect(captured, [3]);

        // Прошло 3 минуты, салон прогрелся до -1°C (> cold.level3StepDown = -2°C)
        async.elapse(const Duration(minutes: 3));
        AutoHeatService().setTemperature(-1.0);
        expect(captured, [3, 2], reason: 't° > -2°C → step-down 3→2 (cold range)');

        // До 9°C. Теперь t° в warm-диапазоне (>= 5°C), где level2StepDown=8°C
        // 9°C > 8°C → step-down 2→1 через temperatureBasedLevel
        async.elapse(const Duration(minutes: 2));
        AutoHeatService().setTemperature(9.0);
        expect(captured, [3, 2, 1], reason: 't° > 8°C (warm level2StepDown) → step-down 2→1');

        // Дальше max-timer доделает
        async.elapse(const Duration(minutes: 10));
        expect(captured, [3, 2, 1, 0]);
        expect(async.nonPeriodicTimerCount, 0);
      });
    });
  });
  // END_BLOCK_ADAPTIVE_STEPDOWN

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
      async.elapse(const Duration(minutes: 8));
      expect(driver, [3], reason: 'driver остановлен');
      expect(passenger, [3, 2], reason: 'passenger max-timer fired');
      async.elapse(const Duration(minutes: 12));
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
  test('scenario-10: custom settings durations drive 3->2->1->0 via max-timer', () {
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

  // START_BLOCK_TEMP_DRIVEN
  group('Temperature-driven поведение', () {
    test(
        'scenario-13: нагрев внутри диапазона не вызывает перезапуск — остаёмся на текущем уровне',
        () {
      fakeAsync((async) {
        final fakeHvac = FakeHvacService();
        final captured = <int>[];

        AutoHeatService().initialize(fakeHvac);
        fakeHvac.emitTemperature(-3.0); // cold
        AutoHeatService().startAutoHeat(UserType.driver, captured.add);
        expect(captured, [3]);

        async.elapse(const Duration(minutes: 3));
        // Небольшое потепление, но всё ещё cold и ниже level3StepDown (-2°C)
        fakeHvac.emitTemperature(-2.5);
        expect(captured, [3], reason: 't° не пересекла порог → уровень не меняется');

        // Max-timer продолжает работать
        async.elapse(const Duration(minutes: 5));
        expect(captured, [3, 2], reason: 'max-timer 8min истёк → уровень 2');
        async.elapse(const Duration(minutes: 5));
        expect(captured, [3, 2, 1]);
        async.elapse(const Duration(minutes: 7));
        expect(captured, [3, 2, 1, 0]);
        expect(async.nonPeriodicTimerCount, 0);
      });
    });

    test(
        'scenario-14: потепление НЕ перезапускает с уровня 3 — продолжает с текущего уровня',
        () {
      fakeAsync((async) {
        final fakeHvac = FakeHvacService();
        final captured = <int>[];

        AutoHeatService().initialize(fakeHvac);
        fakeHvac.emitTemperature(-3.0); // cold
        AutoHeatService().startAutoHeat(UserType.driver, captured.add);
        expect(captured, [3]);

        // Салон прогрелся до 1°C. В cool-диапазоне level3StepDown=2°C, temp 1°C < 2°C
        // → уровень 3 продолжается (не перезапускается, не меняется)
        async.elapse(const Duration(minutes: 3));
        fakeHvac.emitTemperature(1.0);
        expect(captured, [3],
            reason: 't° 1°C < cool.level3StepDown(2°C) → без step-down, без перезапуска');

        // До 3.0°C (> cool.level3StepDown=2°C) → step-down 3→2
        fakeHvac.emitTemperature(3.0);
        expect(captured, [3, 2], reason: 't° > 2°C → step-down 3→2, НЕ перезапуск с 3');

        // Max-timer доделает
        async.elapse(const Duration(minutes: 12));
        expect(captured, [3, 2, 1, 0]);
        expect(async.nonPeriodicTimerCount, 0);
      });
    });

    test('scenario-15: repeated off events — callback(0) однократно', () {
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
        'scenario-16: explicit repeated startAutoHeat перезапускает с уровня 3',
        () {
      fakeAsync((async) {
        final captured = <int>[];
        AutoHeatService().setTemperature(-3.0);
        AutoHeatService().startAutoHeat(UserType.driver, captured.add);
        expect(captured, [3]);
        async.elapse(const Duration(minutes: 3));

        // Явный вызов startAutoHeat сбрасывает _activeLevels → перезапуск с 3
        AutoHeatService().startAutoHeat(UserType.driver, captured.add);

        expect(captured, [3, 3]);
        async.elapse(const Duration(minutes: 8));
        expect(captured, [3, 3, 2]);
      });
    });
  });
  // END_BLOCK_TEMP_DRIVEN

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
