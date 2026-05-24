// FILE: test/unit/hvac_service_test.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Unit-тесты HvacService через мок MethodChannel плагина (V-M-HVAC).
//   SCOPE: исходящие connect/setHvacIntProperty, конверсия температуры из
//          входящего onHvacChangeEvent, multi-listener fan-out/removal,
//          fallback getCabinTemperature, идемпотентность initialize,
//          propagation ошибок connect/write из M-PLUGIN.
//   DEPENDS: M-HVAC, M-PLUGIN, M-ENUMS
//   LINKS: V-M-HVAC, FA-007
//   ROLE: TEST
//   MAP_MODE: LOCALS
// END_MODULE_CONTRACT
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.1.0 - Phase-4 Slice-7: restore FA-007 error propagation scenarios]
// END_CHANGE_SUMMARY

import 'package:android_automotive_plugin/car/hvac_property_ids.dart';
import 'package:android_automotive_plugin/car/vehicle_area_in_out_car.dart';
import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/services/hvac_service.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

import '../_helpers/fake_plugin.dart';
import '../_helpers/logger_test_sink.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late AutomotiveMethodChannelMock mock;
  late HvacService hvac;
  late LoggerTestSink logs;

  setUp(() {
    logs = LoggerTestSink();
    mock = AutomotiveMethodChannelMock()..install();
    hvac = HvacService();
  });

  tearDown(() {
    hvac.dispose();
    mock.uninstall();
    logs.dispose();
  });

  // START_BLOCK_OUTGOING_CALLS
  test(
      'scenario-1: первый setSeatHeatLevel -> connect, затем setHvacIntProperty',
      () async {
    await hvac.setSeatHeatLevel(UserType.driver, 2);
    await pumpEventQueue();
    expect(mock.outgoingMethods, ['connect', 'setHvacIntProperty']);
    expect(
      logs.lines,
      contains(
          '[HvacService][setSeatHeatLevel][BLOCK_SET_SEAT_HEAT_LEVEL] applied | level=2, userType=driver'),
    );
  });

  test('scenario-8: двойной initialize -> connect ровно один раз', () async {
    await hvac.initialize();
    await hvac.initialize();
    await pumpEventQueue();
    expect(mock.outgoingMethods.where((m) => m == 'connect').length, 1);
    expect(
      logs.lines,
      contains('[HvacService][initialize][BLOCK_INITIALIZE] initialized'),
    );
  });

  test('scenario-7: setSeatHeatLevel rethrow при ошибке записи плагина',
      () async {
    mock.throwOnMethods.add('setHvacIntProperty');

    await expectLater(
      hvac.setSeatHeatLevel(UserType.driver, 2),
      throwsA(isA<PlatformException>()),
    );

    expect(hvac.isInitialized, isTrue);
    expect(mock.outgoingMethods, ['connect', 'setHvacIntProperty']);
    expect(
      logs.lines,
      contains(
          '[HvacService][setSeatHeatLevel][BLOCK_SET_SEAT_HEAT_LEVEL] error | level=2, userType=driver, error=PlatformException(TEST_ERROR, mock throw: setHvacIntProperty, null, null)'),
    );
  });

  test('scenario-12: initialize rethrow и не выставляет initialized при ошибке',
      () async {
    mock.throwOnMethods.add('connect');

    await expectLater(
      hvac.initialize(),
      throwsA(isA<PlatformException>()),
    );

    expect(hvac.isInitialized, isFalse);
    expect(mock.outgoingMethods, ['connect']);
    expect(
      logs.lines,
      contains(
          '[HvacService][initialize][BLOCK_INITIALIZE] error | error=PlatformException(TEST_ERROR, mock throw: connect, null, null)'),
    );
  });
  // END_BLOCK_OUTGOING_CALLS

  // START_BLOCK_TEMPERATURE_EVENT
  test('scenario-2: событие InOutCAR_INSIDE -> cabin listener(20.0)', () async {
    final captured = <double>[];
    hvac.addCabinTemperatureListener(captured.add);
    await mock.emitHvacChangeEvent(
      propertyId: CarHvacPropertyIds.ID_HVAC_IN_OUT_TEMP,
      areaId: VehicleAreaInOutCAR.InOutCAR_INSIDE,
      value: 124,
    );
    expect(captured, [20.0]);
    expect(
      logs.lines,
      contains(
          '[HvacService][onHvacChangeEvent][BLOCK_HANDLE_TEMPERATURE_EVENT] cabin temperature changed | celsius=20.0'),
    );
  });

  group('scenario-3: конверсия (raw - 84) / 2', () {
    const cases = <(int, double)>[
      (84, 0.0),
      (74, -5.0),
      (124, 20.0),
      (0, -42.0),
      (200, 58.0),
    ];
    for (final (raw, celsius) in cases) {
      test('raw=$raw -> $celsius', () async {
        final captured = <double>[];
        hvac.addCabinTemperatureListener(captured.add);
        await mock.emitHvacChangeEvent(
          propertyId: CarHvacPropertyIds.ID_HVAC_IN_OUT_TEMP,
          areaId: VehicleAreaInOutCAR.InOutCAR_INSIDE,
          value: raw,
        );
        expect(captured, [celsius]);
      });
    }
  });
  test(
      'scenario-10: cabin temperature listeners fan out and remove independently',
      () async {
    final first = <double>[];
    final second = <double>[];
    void firstListener(double celsius) => first.add(celsius);
    void secondListener(double celsius) => second.add(celsius);

    hvac.addCabinTemperatureListener(firstListener);
    hvac.addCabinTemperatureListener(secondListener);

    await mock.emitHvacChangeEvent(
      propertyId: CarHvacPropertyIds.ID_HVAC_IN_OUT_TEMP,
      areaId: VehicleAreaInOutCAR.InOutCAR_INSIDE,
      value: 124,
    );

    hvac.removeCabinTemperatureListener(firstListener);
    await mock.emitHvacChangeEvent(
      propertyId: CarHvacPropertyIds.ID_HVAC_IN_OUT_TEMP,
      areaId: VehicleAreaInOutCAR.InOutCAR_INSIDE,
      value: 126,
    );

    expect(first, [20.0]);
    expect(second, [20.0, 21.0]);
    expect(hvac.lastCabinTemperature, 21.0);
  });

  test('scenario-11: getCabinTemperature updates cache and notifies listeners',
      () async {
    final captured = <double>[];
    hvac.addCabinTemperatureListener(captured.add);
    mock.hvacIntResponse = 128; // (128 - 84) / 2 = 22.0

    final temp = await hvac.getCabinTemperature();

    expect(temp, 22.0);
    expect(hvac.lastCabinTemperature, 22.0);
    expect(captured, [22.0]);
  });
  // END_BLOCK_TEMPERATURE_EVENT

  // START_BLOCK_EVENT_FILTERING
  test('scenario-4: событие InOutCAR_OUTSIDE не вызывает listener', () async {
    final captured = <double>[];
    hvac.addCabinTemperatureListener(captured.add);
    await mock.emitHvacChangeEvent(
      propertyId: CarHvacPropertyIds.ID_HVAC_IN_OUT_TEMP,
      areaId: VehicleAreaInOutCAR.InOutCAR_OUTSIDE,
      value: 124,
    );
    expect(captured, isEmpty);
  });

  test('scenario-5: чужой propertyId не вызывает listener', () async {
    final captured = <double>[];
    hvac.addCabinTemperatureListener(captured.add);
    await mock.emitHvacChangeEvent(
      propertyId: CarHvacPropertyIds.ID_HVAC_FAN_SPEED_ACK,
      areaId: VehicleAreaInOutCAR.InOutCAR_INSIDE,
      value: 124,
    );
    expect(captured, isEmpty);
  });

  test('scenario-9: событие с value=null не валит изолят, listener не вызван',
      () async {
    final captured = <double>[];
    hvac.addCabinTemperatureListener(captured.add);
    await mock.emitHvacChangeEvent(
      propertyId: CarHvacPropertyIds.ID_HVAC_IN_OUT_TEMP,
      areaId: VehicleAreaInOutCAR.InOutCAR_INSIDE,
      value: null,
    );
    expect(captured, isEmpty);
  });
  // END_BLOCK_EVENT_FILTERING

  // START_BLOCK_READ_FALLBACK
  test('scenario-6: getCabinTemperature при ошибке плагина -> 20.0 fallback',
      () async {
    mock.throwOnMethods.add('getHvacIntProperty');
    final temp = await hvac.getCabinTemperature();
    expect(temp, 20.0);
  });

  test('getCabinTemperature без ошибки конвертирует ответ плагина', () async {
    mock.hvacIntResponse = 124; // (124 - 84) / 2 = 20.0
    final temp = await hvac.getCabinTemperature();
    expect(temp, 20.0);
  });
  // END_BLOCK_READ_FALLBACK
}
