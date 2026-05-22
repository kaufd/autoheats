// FILE: test/unit/preset_service_test.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Unit-тесты PresetService persistence для runtime mode/level fields.
//   SCOPE: createPresetFromCurrentSettings сохраняет heatMode/heatLevel.
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
  test('createPresetFromCurrentSettings сохраняет heatMode и heatLevel',
      () async {
    final service = await buildService();

    final preset = await service.createPresetFromCurrentSettings(
      name: 'Трасса',
      userType: UserType.driver,
      settings: ManualHeatSettings.defaultFor(UserType.driver),
      heatMode: HeatMode.presets,
      heatLevel: 2,
    );

    expect(preset.heatMode, HeatMode.presets);
    expect(preset.heatLevel, 2);

    final loaded = await service.getPresets(UserType.driver);
    expect(loaded.single.heatMode, HeatMode.presets);
    expect(loaded.single.heatLevel, 2);
  });
  // END_BLOCK_CREATE_PRESET_RUNTIME_FIELDS
}
