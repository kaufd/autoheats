// FILE: test/scenarios/walkthrough_log_test.dart
// VERSION: 1.0.0
// PURPOSE: Прогон ключевых сценариев работы режимов (auto / presets / переключения)
//          без UI. Каждый тест собирает все строки Logger через LoggerTestSink
//          и печатает их в stdout — это и есть «результат анализа через логи».
//          Запуск:
//            flutter test test/scenarios/walkthrough_log_test.dart --reporter expanded
//
// Тесты не проверяют точные равенства на каждый чих — они служат журналом
// поведения, который можно читать глазами в выводе flutter test и сверять с
// ожидаемой последовательностью маркеров (BLOCK_SET_MODE, BLOCK_STEP_DOWN,
// BLOCK_SET_HEAT_LEVEL и т.д.). Минимальные expect() в конце фиксируют
// инварианты, чтобы тесты были также и зелёным регрессионным барьером.

import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/cubit/mode_cubit.dart';
import 'package:autoheat/src/models/manual_settings.dart';
import 'package:autoheat/src/models/preset.dart';
import 'package:autoheat/src/services/auto_heat_service.dart';
import 'package:autoheat/src/services/mode_service.dart';
import 'package:autoheat/src/services/preset_service.dart';
import 'package:fake_async/fake_async.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../_helpers/fake_hvac_service.dart';
import '../_helpers/logger_test_sink.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late LoggerTestSink logs;

  setUp(() {
    logs = LoggerTestSink();
  });

  tearDown(() {
    AutoHeatService().dispose();
    logs.dispose();
  });

  void dumpLogs(String title) {
    // ignore: avoid_print -- читаемый дамп для flutter test --reporter expanded
    print('\n\n=== $title — ${logs.lines.length} строк лога ===');
    for (final line in logs.lines) {
      // ignore: avoid_print
      print(line);
    }
    // ignore: avoid_print
    print('=== END $title ===\n');
  }

  ({ModeCubit cubit, FakeHvacService hvac}) buildCubit(
    FakeAsync async, {
    Map<String, Object> seed = const {},
    double initialTemperature = 20.0,
  }) {
    SharedPreferences.setMockInitialValues({});
    late SharedPreferences prefs;
    SharedPreferences.getInstance().then((p) => prefs = p);
    async.flushMicrotasks();

    for (final entry in seed.entries) {
      final v = entry.value;
      if (v is int) {
        prefs.setInt(entry.key, v);
      } else if (v is String) {
        prefs.setString(entry.key, v);
      }
    }
    async.flushMicrotasks();

    final hvac = FakeHvacService()..programmedTemperature = initialTemperature;
    final cubit = ModeCubit(
      ModeService(prefs),
      hvac,
      PresetService(prefs),
    );
    async.flushMicrotasks();
    return (cubit: cubit, hvac: hvac);
  }

  // ----------------------------------------------------------------------
  // Сценарий 1: Auto — холодный старт, полный каскад до 0 при стабильной
  // температуре, проверка plan-key guard (фикс БАГ#1).
  // Ожидаем по логу:
  //   BLOCK_SET_MODE applied (auto)
  //   BLOCK_STEP_DOWN level=3
  //   BLOCK_SCHEDULE_MAX_TIMER nextLevel=2
  //   ... через 5 мин → STEP_DOWN level=2 → SCHEDULE next=1
  //   ... через 3 мин → STEP_DOWN level=1 → SCHEDULE next=0
  //   ... через 7 мин → STEP_DOWN level=0 (max-timer не планируется)
  //   повторный emit(4.0) НЕ должен вызвать новых BLOCK_STEP_DOWN.
  // ----------------------------------------------------------------------
  test('Сценарий 1: auto cold-start + полный каскад + plan-key guard на cool=4',
      () {
    fakeAsync((async) {
      final (:cubit, :hvac) = buildCubit(async);

      cubit.setMode(UserType.driver, 'auto');
      async.flushMicrotasks();

      hvac.emitTemperature(4.0); // cool — старт
      async.flushMicrotasks();

      // cool: level3Duration=5, level2Duration=3, level1Duration=7
      async.elapse(const Duration(minutes: 5)); // 3 → 2
      async.elapse(const Duration(minutes: 3)); // 2 → 1
      async.elapse(const Duration(minutes: 7)); // 1 → 0

      final beforeRepeat = hvac.recordedSetSeatHeatCalls.length;
      hvac.emitTemperature(4.0); // <-- проверка фикса БАГ#1
      async.flushMicrotasks();
      final afterRepeat = hvac.recordedSetSeatHeatCalls.length;

      dumpLogs('Сценарий 1');

      // Должны были увидеть 4 STEP_DOWN: 3,2,1,0.
      final stepDowns = logs.lines
          .where((l) => l.contains('[BLOCK_STEP_DOWN]'))
          .toList();
      expect(stepDowns.length, 4, reason: 'cascade 3→2→1→0');
      // Plan-key guard: повторный emit при том же плане не пересоздаёт level=3.
      expect(afterRepeat, beforeRepeat,
          reason: 'repeat sensor event must not restart cascade (БАГ#1 fix)');
    });
  });

  // ----------------------------------------------------------------------
  // Сценарий 2: Auto — переход через диапазоны (plan-key change). После
  // завершения каскада в cool=4°C приходит -3°C (cold) → должен начаться
  // НОВЫЙ каскад с уровня 3 (план изменился).
  // ----------------------------------------------------------------------
  test('Сценарий 2: auto завершён → смена диапазона (cool→cold) перезапускает',
      () {
    fakeAsync((async) {
      final (:cubit, :hvac) = buildCubit(async);

      cubit.setMode(UserType.driver, 'auto');
      async.flushMicrotasks();
      hvac.emitTemperature(4.0);
      async.flushMicrotasks();
      async.elapse(const Duration(minutes: 5));
      async.elapse(const Duration(minutes: 3));
      async.elapse(const Duration(minutes: 7)); // cool cascade done

      hvac.emitTemperature(-3.0); // переход в cold — план поменялся
      async.flushMicrotasks();

      dumpLogs('Сценарий 2');

      final stepDowns =
          logs.lines.where((l) => l.contains('[BLOCK_STEP_DOWN]')).toList();
      // 4 в cool-каскаде + минимум 1 (level=3) после смены плана.
      expect(stepDowns.length, greaterThanOrEqualTo(5));
      expect(stepDowns.last.contains('level=3'), isTrue,
          reason: 'after plan-key change cascade restarts from level 3');
    });
  });

  // ----------------------------------------------------------------------
  // Сценарий 3: Auto — адаптивный step-down при потеплении.
  // Старт при -8°C (freezing), level=3. Тёплый воздух поднимает t° до +6°C
  // (warm). Естественный уровень в warm при 6°C = 2 (warm.level3StepDown=6).
  // Ожидаем: единичный шаг 3→2 «по температуре», без max-timer.
  // ----------------------------------------------------------------------
  test('Сценарий 3: auto адаптивный step-down при потеплении freezing→warm',
      () {
    fakeAsync((async) {
      final (:cubit, :hvac) = buildCubit(async);

      cubit.setMode(UserType.driver, 'auto');
      async.flushMicrotasks();
      hvac.emitTemperature(-8.0); // freezing — level=3
      async.flushMicrotasks();

      hvac.emitTemperature(6.0); // warm — tempBasedLevel(6, warm) = 2 (>=6)
      async.flushMicrotasks();

      dumpLogs('Сценарий 3');

      final lastLevel = hvac.recordedSetSeatHeatCalls.last.level;
      expect(lastLevel, 2,
          reason: 'adaptive step-down должен прыгнуть к 2 без max-timer');
    });
  });

  // ----------------------------------------------------------------------
  // Сценарий 4: Пресет — гейт по threshold на старте + фиксированный каскад
  // независимо от температуры после старта.
  // threshold=10, durations 1/1/1, температура старта 25°C (выше threshold) →
  // ожидаем callback(0) и НЕТ запуска каскада. Затем эмулируем падение до
  // 5°C → каскад стартует. Во время каскада подскок до 25°C → каскад НЕ
  // прерывается (isPresetRunning early-return).
  // ----------------------------------------------------------------------
  test('Сценарий 4: preset threshold-gate + фиксированный каскад', () {
    fakeAsync((async) {
      final (:cubit, :hvac) = buildCubit(async);

      final preset = Preset(
        id: 'p-test',
        name: 'TestPreset',
        userType: UserType.driver,
        settings: const ManualHeatSettings(
          autoHeatLevels: [
            AutoHeatLevel(duration: 1, level: 1),
            AutoHeatLevel(duration: 1, level: 2),
            AutoHeatLevel(duration: 1, level: 3),
          ],
          temperatureThreshold: 10.0,
        ),
        createdAt: DateTime.parse('2026-01-02T03:04:05.000'),
      );

      hvac.emitTemperature(25.0); // выше threshold
      async.flushMicrotasks();

      cubit.applyPreset(preset);
      async.flushMicrotasks();
      final callsAtGate = hvac.recordedSetSeatHeatCalls.length;

      hvac.emitTemperature(5.0); // ниже threshold — старт каскада
      async.flushMicrotasks();

      async.elapse(const Duration(minutes: 1)); // 3 → 2
      hvac.emitTemperature(25.0); // <-- температура выше threshold, но каскад НЕ прерывается
      async.flushMicrotasks();
      async.elapse(const Duration(minutes: 1)); // 2 → 1
      async.elapse(const Duration(minutes: 1)); // 1 → 0

      dumpLogs('Сценарий 4');

      final stepDowns =
          logs.lines.where((l) => l.contains('[BLOCK_STEP_DOWN]')).toList();
      // Гейт на старте: callback(0). Затем 4 step-down: 3,2,1,0.
      // 0 на гейте может быть как BLOCK_STEP_DOWN (если callback(0) идёт
      // через _stepDownLevel) либо как обычный setSeatHeatLevel.
      // Сейчас off-branch шлёт callback(0) НЕ через _stepDownLevel, поэтому
      // BLOCK_STEP_DOWN ровно 4.
      expect(stepDowns.length, 4,
          reason: 'fixed-duration cascade: 3,2,1,0 (no abort на 25°C mid-flight)');
      expect(callsAtGate, greaterThanOrEqualTo(1),
          reason: 'threshold gate должен послать level=0 на старте');
    });
  });

  // ----------------------------------------------------------------------
  // Сценарий 6: debug OFF — injected temperature должна уступить место
  // реальной. Имитируем то, что делает CabinTemperatureDisplay._toggleDebugMode
  // в выключенной ветке: AutoHeatService.setTemperature(-3) (инжектор), затем
  // hvacService.getCabinTemperature() (восстановление). После восстановления
  // авто-режим должен ВЫКЛЮЧИТЬСЯ (реальная t° 25°C → off-диапазон).
  // ----------------------------------------------------------------------
  test('Сценарий 6: debug OFF восстанавливает реальную температуру', () {
    fakeAsync((async) {
      final (:cubit, :hvac) = buildCubit(async, initialTemperature: 25.0);

      cubit.setMode(UserType.driver, 'auto');
      async.flushMicrotasks();

      // Инжектор debug-режима: −3°C → cold-диапазон, каскад с уровня 3.
      AutoHeatService().setTemperature(-3.0);
      async.flushMicrotasks();
      final levelDuringInject = hvac.recordedSetSeatHeatCalls.last.level;

      // Имитация выключения debug: getCabinTemperature() публикует реальные
      // 25°C, AutoHeatService._handleCabinTemperature → off-branch → 0.
      hvac.getCabinTemperature();
      async.flushMicrotasks();
      final levelAfterRestore = hvac.recordedSetSeatHeatCalls.last.level;

      dumpLogs('Сценарий 6');

      expect(levelDuringInject, 3,
          reason: 'injected -3°C → cold cascade level 3');
      expect(levelAfterRestore, 0,
          reason: 'restore real 25°C → off-range → level 0');
    });
  });

  // ----------------------------------------------------------------------
  // Сценарий 5: переключения режимов manual ↔ auto ↔ presets.
  // Прогон: setMode(manual) → setHeatLevel(2) → setMode(auto) →
  // applyPreset(P) → setMode(manual). Логи должны показать чёткую
  // последовательность BLOCK_SET_MODE и BLOCK_STOP (AutoHeatService) при
  // выходе из auto/presets, единый путь через setMode.
  // ----------------------------------------------------------------------
  test('Сценарий 5: переключения режимов manual ↔ auto ↔ presets', () {
    fakeAsync((async) {
      final (:cubit, :hvac) = buildCubit(async, initialTemperature: 4.0);

      // 1) manual + level 2
      cubit.setMode(UserType.driver, 'manual');
      async.flushMicrotasks();
      cubit.setHeatLevel(UserType.driver, 2);
      async.flushMicrotasks();

      // 2) переход в auto
      cubit.setMode(UserType.driver, 'auto');
      async.flushMicrotasks();
      hvac.emitTemperature(4.0);
      async.flushMicrotasks();

      // 3) apply preset (presets-режим)
      final preset = Preset(
        id: 'p-toggle',
        name: 'TogglePreset',
        userType: UserType.driver,
        settings: ManualHeatSettings.defaultFor(UserType.driver),
        createdAt: DateTime.parse('2026-01-02T03:04:05.000'),
      );
      cubit.applyPreset(preset);
      async.flushMicrotasks();

      // 4) обратно в manual через setMode (унифицированный путь, фикс п.1)
      cubit.setMode(UserType.driver, 'manual');
      async.flushMicrotasks();

      dumpLogs('Сценарий 5');

      final modeChanges = logs.lines
          .where((l) => l.contains('[BLOCK_SET_MODE]') && l.contains('applied'))
          .toList();
      final stopMarkers =
          logs.lines.where((l) => l.contains('[BLOCK_STOP]')).toList();

      // 4 setMode: manual → auto → presets (внутри applyPreset) → manual.
      expect(modeChanges.length, greaterThanOrEqualTo(4));
      // STOP AutoHeatService при выходе из auto и presets — минимум 2 раза.
      expect(stopMarkers.length, greaterThanOrEqualTo(2),
          reason: 'AutoHeatService.stopAutoHeat должен вызваться при '
              'каждом уходе из auto/presets');
    });
  });
}
