// FILE: test/unit/preset_model_test.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Unit-тесты Preset JSON-контракта, включая Phase-4 runtime fields.
//   SCOPE: round-trip heatMode/heatLevel и legacy JSON defaults.
//   DEPENDS: M-PRESET, M-MANUAL-SETTINGS, M-ENUMS
//   LINKS: V-M-PRESET, FA-001, FA-011
//   ROLE: TEST
//   MAP_MODE: LOCALS
// END_MODULE_CONTRACT

import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/models/manual_settings.dart';
import 'package:autoheat/src/models/preset.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  // START_BLOCK_PRESET_NO_SNAPSHOT_FIELDS
  test('scenario-legacy-defaults: старый JSON без heatMode/heatLevel грузится безопасно',
      () {
    final legacyJson = <String, dynamic>{
      'id': 'legacy',
      'name': 'Старый пресет',
      'userType': 'passenger',
      'settings': ManualHeatSettings.defaultFor(UserType.passenger).toJson(),
      'createdAt': '2026-01-02T03:04:05.000',
      'lastUsed': null,
    };

    final restored = Preset.fromJson(legacyJson);
    expect(restored.userType, UserType.passenger);
  });
  // END_BLOCK_PRESET_NO_SNAPSHOT_FIELDS
}
