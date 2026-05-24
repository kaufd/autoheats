// FILE: test/unit/preset_cubit_test.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Unit-тесты PresetCubit state-machine для selected preset per user.
//   SCOPE: loadAllPresets восстанавливает persisted selection; apply/delete
//          синхронизируют PresetService и PresetState.
//   DEPENDS: M-PRESET, M-ENUMS, M-MANUAL-SETTINGS
//   LINKS: V-M-PRESET, FA-011, DF-PRESET-APPLY
//   ROLE: TEST
//   MAP_MODE: LOCALS
// END_MODULE_CONTRACT

import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/cubit/preset_cubit.dart';
import 'package:autoheat/src/models/manual_settings.dart';
import 'package:autoheat/src/models/preset.dart';
import 'package:autoheat/src/services/preset_service.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  Future<(PresetCubit, PresetService)> buildCubit() async {
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final service = PresetService(prefs);
    final cubit = PresetCubit(service);
    addTearDown(cubit.close);
    return (cubit, service);
  }

  Preset preset(UserType userType, String id) {
    return Preset(
      id: id,
      name: 'Preset $id',
      userType: userType,
      settings: ManualHeatSettings.defaultFor(userType),
      createdAt: DateTime.parse('2026-01-02T03:04:05.000'),
    );
  }

  // START_BLOCK_PRESET_CUBIT_SELECTION
  test('scenario-1: loadAllPresets восстанавливает selected preset per user',
      () async {
    final (cubit, service) = await buildCubit();
    final driverPreset = preset(UserType.driver, 'driver-selected');
    final passengerPreset = preset(UserType.passenger, 'passenger-selected');
    await service.savePreset(driverPreset);
    await service.savePreset(passengerPreset);
    await service.setSelectedPresetId(UserType.driver, driverPreset.id);
    await service.setSelectedPresetId(UserType.passenger, passengerPreset.id);

    await cubit.loadAllPresets();

    expect(cubit.state.selectedPresetFor(UserType.driver), driverPreset);
    expect(cubit.state.selectedPresetFor(UserType.passenger), passengerPreset);
  });

  test('scenario-2: applyPreset сохраняет selected preset id', () async {
    final (cubit, service) = await buildCubit();
    final driverPreset = preset(UserType.driver, 'driver-selected');
    await service.savePreset(driverPreset);
    await cubit.loadAllPresets();

    await cubit.applyPreset(driverPreset);

    expect(await service.getSelectedPresetId(UserType.driver), driverPreset.id);
    expect(cubit.state.selectedPresetFor(UserType.driver), driverPreset);
  });

  test('scenario-3: deletePreset выбранного пресета очищает selection',
      () async {
    final (cubit, service) = await buildCubit();
    final driverPreset = preset(UserType.driver, 'driver-selected');
    await service.savePreset(driverPreset);
    await service.setSelectedPresetId(UserType.driver, driverPreset.id);
    await cubit.loadAllPresets();

    await cubit.deletePreset(driverPreset.id, UserType.driver);

    expect(await service.getSelectedPresetId(UserType.driver), isNull);
    expect(cubit.state.selectedPresetFor(UserType.driver), isNull);
  });
  // END_BLOCK_PRESET_CUBIT_SELECTION
}
