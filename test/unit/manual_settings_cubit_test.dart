// FILE: test/unit/manual_settings_cubit_test.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Unit-тесты ManualSettingsCubit state/error behavior.
//   SCOPE: clear stale error after successful ManualSettings operations.
//   DEPENDS: M-MANUAL-SETTINGS, M-ENUMS
//   LINKS: V-M-MANUAL-SETTINGS, FA-012
//   ROLE: TEST
//   MAP_MODE: LOCALS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   FailingManualSettingsService - controllable save failure harness
//   scenario-2 - successful update clears stale error
// END_MODULE_MAP

import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/cubit/manual_settings_cubit.dart';
import 'package:autoheat/src/models/manual_settings.dart';
import 'package:autoheat/src/services/manual_settings_service.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

class FailingManualSettingsService extends ManualSettingsService {
  bool failNextSave = false;

  FailingManualSettingsService(super.prefs);

  @override
  Future<void> saveSettings(ManualHeatSettings settings, UserType userType) {
    if (failNextSave) {
      failNextSave = false;
      throw StateError('planned save failure');
    }
    return super.saveSettings(settings, userType);
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  // START_BLOCK_CLEAR_STALE_ERROR
  test('scenario-2: successful update clears stale manual settings error',
      () async {
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final service = FailingManualSettingsService(prefs);
    final cubit = ManualSettingsCubit(service);
    addTearDown(cubit.close);

    service.failNextSave = true;
    await cubit.updateTemperatureThreshold(UserType.driver, 12);

    expect(cubit.state.error, contains('planned save failure'));

    await cubit.updateTemperatureThreshold(UserType.driver, 13);

    expect(cubit.state.error, isNull);
  });
  // END_BLOCK_CLEAR_STALE_ERROR
}
