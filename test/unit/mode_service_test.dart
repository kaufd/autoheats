// FILE: test/unit/mode_service_test.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Unit-тесты ModeService — persistence режимов и уровней подогрева.
//   SCOPE: initializeDefaults, round-trip setMode/getMode и setHeatLevel/
//          getHeatLevel, дефолты на отсутствующих ключах, устойчивость к мусору.
//   DEPENDS: M-MODE, M-ENUMS
//   LINKS: V-M-MODE
//   ROLE: TEST
//   MAP_MODE: LOCALS
// END_MODULE_CONTRACT

import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/services/mode_service.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../_helpers/logger_test_sink.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late LoggerTestSink logs;

  setUp(() {
    logs = LoggerTestSink();
  });

  tearDown(() {
    logs.dispose();
  });

  // Сидирование через реальный API prefs — не зависит от соглашения о
  // префиксах ключей в setMockInitialValues.
  Future<ModeService> buildService(Map<String, Object> seed) async {
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
    return ModeService(prefs);
  }

  // START_BLOCK_INITIALIZE_DEFAULTS
  group('initializeDefaults', () {
    test('на пустых prefs выставляет manual/0 для обоих сидений', () async {
      final service = await buildService({});
      await service.initializeDefaults();
      expect(service.getMode(UserType.driver), HeatMode.manual);
      expect(service.getMode(UserType.passenger), HeatMode.manual);
      expect(service.getHeatLevel(UserType.driver), 0);
      expect(service.getHeatLevel(UserType.passenger), 0);
    });

    test('не перетирает существующие значения', () async {
      final service = await buildService(
        {'driver_mode': 'auto', 'driver_heat_level': 2},
      );
      await service.initializeDefaults();
      expect(service.getMode(UserType.driver), HeatMode.auto);
      expect(service.getHeatLevel(UserType.driver), 2);
    });
  });
  // END_BLOCK_INITIALIZE_DEFAULTS

  // START_BLOCK_ROUNDTRIP
  group('round-trip persistence', () {
    test('setMode -> getMode', () async {
      final service = await buildService({});
      await service.setMode(UserType.driver, HeatMode.auto);
      expect(
        logs.lines,
        contains(
            '[ModeService][setMode][BLOCK_SET_MODE] persisted | userType=driver, mode=auto'),
      );
      expect(service.getMode(UserType.driver), HeatMode.auto);
    });

    test('setHeatLevel -> getHeatLevel', () async {
      final service = await buildService({});
      await service.setHeatLevel(UserType.passenger, 3);
      expect(
        logs.lines,
        contains(
            '[ModeService][setHeatLevel][BLOCK_SET_HEAT_LEVEL] persisted | userType=passenger, level=3'),
      );
      expect(service.getHeatLevel(UserType.passenger), 3);
    });
  });
  // END_BLOCK_ROUNDTRIP

  // START_BLOCK_DEFAULTS_AND_GARBAGE
  group('Дефолты и устойчивость к мусору', () {
    test('getMode на отсутствующем ключе -> manual', () async {
      final service = await buildService({});
      expect(service.getMode(UserType.driver), HeatMode.manual);
    });

    test('getHeatLevel на отсутствующем ключе -> 0', () async {
      final service = await buildService({});
      expect(service.getHeatLevel(UserType.driver), 0);
    });

    test('getMode на мусорном значении -> manual (orElse)', () async {
      final service = await buildService({'driver_mode': 'garbage'});
      expect(service.getMode(UserType.driver), HeatMode.manual);
    });
  });
  // END_BLOCK_DEFAULTS_AND_GARBAGE

  // START_BLOCK_GET_ALL_MODES
  test('getAllModes возвращает записи для обоих сидений', () async {
    final service = await buildService({
      'driver_mode': 'auto',
      'driver_heat_level': 1,
      'passenger_mode': 'manual',
      'passenger_heat_level': 0,
    });
    final all = service.getAllModes();
    expect(all[UserType.driver]!['mode'], HeatMode.auto);
    expect(all[UserType.driver]!['heatLevel'], 1);
    expect(all[UserType.passenger]!['mode'], HeatMode.manual);
    expect(all[UserType.passenger]!['heatLevel'], 0);
  });
  // END_BLOCK_GET_ALL_MODES
}
