// FILE: test/unit/cabin_temperature_cubit_test.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Unit-тесты CabinTemperatureCubit — UI-state проекция температуры салона.
//   SCOPE: initial read, cached emitCurrent, listener updates, unsubscribe on close.
//   DEPENDS: M-CABIN-TEMPERATURE, M-HVAC
//   LINKS: V-M-CABIN-TEMPERATURE, FA-003
//   ROLE: TEST
//   MAP_MODE: LOCALS
// END_MODULE_CONTRACT

import 'package:autoheat/src/cubit/cabin_temperature_cubit.dart';
import 'package:flutter_test/flutter_test.dart';

import '../_helpers/fake_hvac_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  // START_BLOCK_INITIAL_READ
  test('scenario-1: initial read emits programmed cabin temperature', () async {
    final fakeHvac = FakeHvacService()..programmedTemperature = 18.5;
    final cubit = CabinTemperatureCubit(fakeHvac);
    addTearDown(cubit.close);

    await pumpEventQueue();

    expect(cubit.state.celsius, 18.5);
    expect(cubit.state.isLoading, isFalse);
    expect(fakeHvac.getCabinTemperatureCallCount, 1);
  });
  // END_BLOCK_INITIAL_READ

  // START_BLOCK_CACHED_EMIT
  test('scenario-2: cached temperature emits without second initial read',
      () async {
    final fakeHvac = FakeHvacService();
    fakeHvac.emitTemperature(16.5);

    final cubit = CabinTemperatureCubit(fakeHvac);
    addTearDown(cubit.close);

    await pumpEventQueue();

    expect(cubit.state.celsius, 16.5);
    expect(cubit.state.isLoading, isFalse);
    expect(fakeHvac.getCabinTemperatureCallCount, 0);
  });
  // END_BLOCK_CACHED_EMIT

  // START_BLOCK_LISTENER_UPDATE
  test('scenario-3: HVAC event updates cubit state without auto mode',
      () async {
    final fakeHvac = FakeHvacService()..programmedTemperature = 20.0;
    final cubit = CabinTemperatureCubit(fakeHvac);
    addTearDown(cubit.close);
    await pumpEventQueue();

    fakeHvac.emitTemperature(-3.0);
    await pumpEventQueue();

    expect(cubit.state.celsius, -3.0);
    expect(cubit.state.isLoading, isFalse);
  });
  // END_BLOCK_LISTENER_UPDATE

  // START_BLOCK_CLOSE_UNSUBSCRIBE
  test('scenario-4: close removes listener', () async {
    final fakeHvac = FakeHvacService()..programmedTemperature = 20.0;
    final cubit = CabinTemperatureCubit(fakeHvac);
    await pumpEventQueue();
    expect(cubit.state.celsius, 20.0);

    await cubit.close();
    fakeHvac.emitTemperature(7.0);
    await pumpEventQueue();

    expect(cubit.state.celsius, 20.0);
  });
  // END_BLOCK_CLOSE_UNSUBSCRIBE
}
