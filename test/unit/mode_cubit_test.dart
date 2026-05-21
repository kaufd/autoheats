// FILE: test/unit/mode_cubit_test.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Unit-тесты ModeCubit — мост UI ↔ persistence ↔ HVAC ↔ авторежим.
//   SCOPE: setHeatLevel, setMode (manual/auto), восстановление из prefs,
//          устойчивость к мусору, проброс авторежима в HvacService.
//   DEPENDS: M-MODE, M-HVAC, M-AUTO-HEAT, M-ENUMS
//   LINKS: V-M-MODE
//   ROLE: TEST
//   MAP_MODE: LOCALS
// END_MODULE_CONTRACT

import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/cubit/mode_cubit.dart';
import 'package:autoheat/src/cubit/mode_state_cubit.dart';
import 'package:autoheat/src/services/auto_heat_service.dart';
import 'package:autoheat/src/services/mode_service.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../_helpers/fake_hvac_service.dart';

// ВАЖНО ПРО ПОРЯДОК ТЕСТОВ:
// ModeCubit создаёт AutoHeatService() — синглтон. Его _currentTemperature
// нельзя сбросить в null публичным API. Тесты, чувствительные к нулевой
// температуре (восстановление auto-режима из prefs, setMode auto без
// события датчика), обязаны идти ДО любого теста, который вызывает
// emitTemperature. Поэтому emit-тесты (scenario-3, scenario-4) — последние.

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  tearDown(() {
    AutoHeatService().dispose();
  });

  ModeState stateOf(ModeCubit cubit, UserType user) =>
      cubit.state.states.firstWhere((s) => s.userType == user);

  Future<(ModeCubit, FakeHvacService, SharedPreferences)> buildCubit(
      Map<String, Object> seed) async {
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    for (final entry in seed.entries) {
      final value = entry.value;
      if (value is int) {
        await prefs.setInt(entry.key, value);
      } else if (value is String) {
        await prefs.setString(entry.key, value);
      }
    }
    final fakeHvac = FakeHvacService();
    final cubit = ModeCubit(ModeService(prefs), fakeHvac);
    addTearDown(cubit.close);
    await pumpEventQueue();
    return (cubit, fakeHvac, prefs);
  }

  // START_BLOCK_COLD_START
  test('scenario-5: холодный старт с пустыми prefs -> оба (manual, 0)', () async {
    final (cubit, fakeHvac, _) = await buildCubit({});
    expect(stateOf(cubit, UserType.driver).heatMode, HeatMode.manual);
    expect(stateOf(cubit, UserType.driver).heatLevel, 0);
    expect(stateOf(cubit, UserType.passenger).heatMode, HeatMode.manual);
    expect(stateOf(cubit, UserType.passenger).heatLevel, 0);
    expect(fakeHvac.recordedSetSeatHeatCalls, isEmpty);
  });
  // END_BLOCK_COLD_START

  // START_BLOCK_RESTORE_FROM_PREFS
  test('scenario-6: восстановление driver(auto, 1) из prefs', () async {
    final (cubit, _, _) = await buildCubit({
      'driver_mode': 'auto',
      'driver_heat_level': 1,
    });
    expect(stateOf(cubit, UserType.driver).heatMode, HeatMode.auto);
    expect(stateOf(cubit, UserType.driver).heatLevel, 1);
    expect(stateOf(cubit, UserType.passenger).heatMode, HeatMode.manual);
  });
  // END_BLOCK_RESTORE_FROM_PREFS

  // START_BLOCK_GARBAGE_PREFS
  test('scenario-7: мусорное значение режима в prefs -> manual, без падения',
      () async {
    final (cubit, _, _) = await buildCubit({'driver_mode': 'garbage'});
    expect(stateOf(cubit, UserType.driver).heatMode, HeatMode.manual);
  });
  // END_BLOCK_GARBAGE_PREFS

  // START_BLOCK_SET_HEAT_LEVEL
  test('scenario-1: setHeatLevel пишет в prefs, HVAC и состояние', () async {
    final (cubit, fakeHvac, prefs) = await buildCubit({});
    await cubit.setHeatLevel(UserType.driver, 2);
    expect(stateOf(cubit, UserType.driver).heatLevel, 2);
    expect(fakeHvac.recordedSetSeatHeatCalls,
        [(userType: UserType.driver, level: 2)]);
    expect(prefs.getInt('driver_heat_level'), 2);
  });

  test('scenario-8: повторный setHeatLevel(2) вызывает HVAC каждый раз (O-1)',
      () async {
    final (cubit, fakeHvac, _) = await buildCubit({});
    await cubit.setHeatLevel(UserType.driver, 2);
    await cubit.setHeatLevel(UserType.driver, 2);
    expect(fakeHvac.recordedSetSeatHeatCalls, [
      (userType: UserType.driver, level: 2),
      (userType: UserType.driver, level: 2),
    ]);
  });
  // END_BLOCK_SET_HEAT_LEVEL

  // START_BLOCK_SET_MODE
  test('scenario-2: setMode auto -> состояние и prefs обновлены', () async {
    final (cubit, _, prefs) = await buildCubit({});
    await cubit.setMode(UserType.driver, HeatMode.auto.name);
    expect(stateOf(cubit, UserType.driver).heatMode, HeatMode.auto);
    expect(prefs.getString('driver_mode'), 'auto');
  });
  // END_BLOCK_SET_MODE

  // START_BLOCK_AUTO_STOP
  test('scenario-3: auto -> manual останавливает авторежим', () async {
    final (cubit, fakeHvac, _) = await buildCubit({});
    await cubit.setMode(UserType.driver, HeatMode.auto.name);
    await cubit.setMode(UserType.driver, HeatMode.manual.name);
    final levelBefore = stateOf(cubit, UserType.driver).heatLevel;

    fakeHvac.emitTemperature(-3.0);
    await pumpEventQueue();

    expect(stateOf(cubit, UserType.driver).heatLevel, levelBefore,
        reason: 'после stopAutoHeat событие датчика не меняет уровень');
    expect(fakeHvac.recordedSetSeatHeatCalls, isEmpty);
  });
  // END_BLOCK_AUTO_STOP

  // START_BLOCK_AUTO_END_TO_END
  test('scenario-4: auto + событие датчика -> уровень 3 через HvacService',
      () async {
    final (cubit, fakeHvac, _) = await buildCubit({});
    await cubit.setMode(UserType.driver, HeatMode.auto.name);

    fakeHvac.emitTemperature(-3.0);
    await pumpEventQueue();

    expect(stateOf(cubit, UserType.driver).heatLevel, 3);
    expect(fakeHvac.recordedSetSeatHeatCalls,
        contains((userType: UserType.driver, level: 3)));
  });
  // END_BLOCK_AUTO_END_TO_END
}
