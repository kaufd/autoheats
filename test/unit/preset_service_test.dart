// FILE: test/unit/preset_service_test.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Unit-тесты PresetService persistence для runtime mode/level fields
//            и выбранного preset-id per UserType.
//   SCOPE: createPresetFromCurrentSettings сохраняет heatMode/heatLevel;
//          selected preset id сохраняется/очищается отдельно для driver/passenger.
//   DEPENDS: M-PRESET, M-ENUMS, M-MANUAL-SETTINGS
//   LINKS: V-M-PRESET, FA-001, FA-011
//   ROLE: TEST
//   MAP_MODE: LOCALS
// END_MODULE_CONTRACT

import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/models/manual_settings.dart';
import 'package:autoheat/src/services/preset_service.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  Future<PresetService> buildService() async {
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    return PresetService(prefs);
  }

  // START_BLOCK_CREATE_PRESET_RUNTIME_FIELDS
  test('createPresetFromCurrentSettings сохраняет пресет с настройками',
      () async {
    final service = await buildService();

    await service.createPresetFromCurrentSettings(
      name: 'Трасса',
      userType: UserType.driver,
      settings: ManualHeatSettings.defaultFor(UserType.driver),
    );

    final loaded = await service.getPresets(UserType.driver);
    expect(loaded.single.name, 'Трасса');
  });
  // END_BLOCK_CREATE_PRESET_RUNTIME_FIELDS

  // START_BLOCK_SELECTED_PRESET_ID
  test('scenario-selected-id: selected preset id хранится отдельно для сидений',
      () async {
    final service = await buildService();

    await service.setSelectedPresetId(UserType.driver, 'driver-winter');
    await service.setSelectedPresetId(UserType.passenger, 'passenger-winter');

    expect(await service.getSelectedPresetId(UserType.driver), 'driver-winter');
    expect(
      await service.getSelectedPresetId(UserType.passenger),
      'passenger-winter',
    );

    await service.clearSelectedPresetId(UserType.driver);

    expect(await service.getSelectedPresetId(UserType.driver), isNull);
    expect(
      await service.getSelectedPresetId(UserType.passenger),
      'passenger-winter',
    );
  });

  test('scenario-delete-selected: deletePreset очищает выбранный id', () async {
    final service = await buildService();
    final preset = await service.createPresetFromCurrentSettings(
      name: 'Зима',
      userType: UserType.driver,
      settings: ManualHeatSettings.defaultFor(UserType.driver),
    );
    await service.setSelectedPresetId(UserType.driver, preset.id);

    await service.deletePreset(preset.id, UserType.driver);

    expect(await service.getSelectedPresetId(UserType.driver), isNull);
  });
  // END_BLOCK_SELECTED_PRESET_ID
}
