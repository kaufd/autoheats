// FILE: test/unit/mode_cubit_test.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Unit-тесты ModeCubit — мост UI ↔ persistence ↔ HVAC ↔ авторежим.
//   SCOPE: setHeatLevel, setMode (manual/auto), восстановление из prefs,
//          устойчивость к мусору, проброс авторежима в HvacService.
//   DEPENDS: M-MODE, M-HVAC, M-AUTO-HEAT, M-ENUMS, M-MANUAL-SETTINGS
//   LINKS: V-M-MODE
//   ROLE: TEST
//   MAP_MODE: LOCALS
// END_MODULE_CONTRACT

import 'dart:convert';

import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/cubit/mode_cubit.dart';
import 'package:autoheat/src/cubit/mode_state_cubit.dart';
import 'package:autoheat/src/models/manual_settings.dart';
import 'package:autoheat/src/models/preset.dart';
import 'package:autoheat/src/services/auto_heat_service.dart';
import 'package:autoheat/src/services/manual_settings_service.dart';
import 'package:autoheat/src/services/mode_service.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../_helpers/fake_hvac_service.dart';
import '../_helpers/logger_test_sink.dart';

// ВАЖНО ПРО ПОРЯДОК ТЕСТОВ:
// ModeCubit создаёт AutoHeatService() — синглтон. Его _currentTemperature
// нельзя сбросить в null публичным API. Тесты, чувствительные к нулевой
// температуре (восстановление auto-режима из prefs, setMode auto без
// события датчика), обязаны идти ДО любого теста, который вызывает
// emitTemperature. Поэтому emit-тесты (scenario-3, scenario-12, scenario-4) — последние.

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
    final cubit = ModeCubit(
      ModeService(prefs),
      fakeHvac,
      ManualSettingsService(prefs),
    );
    addTearDown(cubit.close);
    await pumpEventQueue();
    return (cubit, fakeHvac, prefs);
  }

  Preset preset({
    UserType userType = UserType.driver,
    HeatMode heatMode = HeatMode.presets,
    int heatLevel = 2,
  }) {
    return Preset(
      id: 'preset-${userType.name}',
      name: 'Зима ${userType.name}',
      userType: userType,
      settings: ManualHeatSettings.defaultFor(userType),
      heatMode: heatMode,
      heatLevel: heatLevel,
      createdAt: DateTime.parse('2026-01-02T03:04:05.000'),
    );
  }

  // START_BLOCK_COLD_START
  test('scenario-5: холодный старт с пустыми prefs -> оба (manual, 0)',
      () async {
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
    expect(
      logs.lines,
      contains(
          '[ModeCubit][setHeatLevel][BLOCK_SET_HEAT_LEVEL] applied | userType=driver, level=2'),
    );
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
    expect(
      logs.lines,
      contains(
          '[ModeCubit][setMode][BLOCK_SET_MODE] applied | userType=driver, mode=auto'),
    );
  });
  // END_BLOCK_SET_MODE

  // START_BLOCK_APPLY_PRESET
  test('scenario-9: applyPreset выставляет mode/level, prefs и HVAC', () async {
    final (cubit, fakeHvac, prefs) = await buildCubit({});

    await cubit.applyPreset(preset(heatMode: HeatMode.presets, heatLevel: 2));

    expect(stateOf(cubit, UserType.driver).heatMode, HeatMode.presets);
    expect(stateOf(cubit, UserType.driver).heatLevel, 2);
    expect(prefs.getString('driver_mode'), 'presets');
    expect(prefs.getInt('driver_heat_level'), 2);
    expect(fakeHvac.recordedSetSeatHeatCalls, [
      (userType: UserType.driver, level: 2),
    ]);
    expect(
      logs.lines,
      contains(
          '[ModeCubit][applyPreset][BLOCK_APPLY_PRESET] applied | userType=driver, mode=presets, level=2, presetId=preset-driver'),
    );
  });

  test('scenario-10: setMode manual с активным уровнем отправляет HVAC 0',
      () async {
    final (cubit, fakeHvac, prefs) = await buildCubit({});
    await cubit.setHeatLevel(UserType.driver, 3);
    fakeHvac.recordedSetSeatHeatCalls.clear();

    await cubit.setMode(UserType.driver, HeatMode.manual.name);

    expect(stateOf(cubit, UserType.driver).heatMode, HeatMode.manual);
    expect(stateOf(cubit, UserType.driver).heatLevel, 0);
    expect(prefs.getString('driver_mode'), 'manual');
    expect(prefs.getInt('driver_heat_level'), 0);
    expect(fakeHvac.recordedSetSeatHeatCalls, [
      (userType: UserType.driver, level: 0),
    ]);
  });

  test(
      'scenario-11: toggleHeatLevel из presets последовательно включает manual level 1',
      () async {
    final (cubit, fakeHvac, prefs) = await buildCubit({});
    await cubit.applyPreset(preset(heatMode: HeatMode.presets, heatLevel: 2));
    fakeHvac.recordedSetSeatHeatCalls.clear();

    await cubit.toggleHeatLevel(UserType.driver);

    expect(stateOf(cubit, UserType.driver).heatMode, HeatMode.manual);
    expect(stateOf(cubit, UserType.driver).heatLevel, 1);
    expect(prefs.getString('driver_mode'), 'manual');
    expect(prefs.getInt('driver_heat_level'), 1);
    expect(fakeHvac.recordedSetSeatHeatCalls, [
      (userType: UserType.driver, level: 1),
    ]);
  });
  // END_BLOCK_APPLY_PRESET

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

  test('scenario-12: auto mode uses persisted ManualSettings threshold',
      () async {
    final customSettings = ManualHeatSettings(
      autoHeatLevels: const [
        AutoHeatLevel(level: 1, duration: 1),
        AutoHeatLevel(level: 2, duration: 2),
        AutoHeatLevel(level: 3, duration: 3),
      ],
      temperatureThreshold: -10.0,
    );
    final (cubit, fakeHvac, prefs) = await buildCubit({});
    await prefs.setString(
      'manual_settings_driver',
      json.encode(customSettings.toJson()),
    );

    await cubit.setMode(UserType.driver, HeatMode.auto.name);
    fakeHvac.emitTemperature(-3.0);
    await pumpEventQueue();

    expect(stateOf(cubit, UserType.driver).heatLevel, 0);
    expect(
      fakeHvac.recordedSetSeatHeatCalls,
      contains((userType: UserType.driver, level: 0)),
    );
  });

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
