// FILE: test/unit/app_enums_test.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Unit-тесты HeatMode/UserType и их fromString-расширений (V-M-ENUMS).
//   SCOPE: round-trip enum.name ↔ fromString, orElse-фоллбэк, case-sensitivity,
//          regression-snapshot строковых имён (контракт SharedPreferences).
//   DEPENDS: M-ENUMS
//   LINKS: V-M-ENUMS
//   ROLE: TEST
//   MAP_MODE: LOCALS
// END_MODULE_CONTRACT

import 'package:autoheat/src/app_enums.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  // START_BLOCK_HEATMODE_ROUNDTRIP
  group('HeatMode round-trip (scenario-1)', () {
    for (final mode in HeatMode.values) {
      test('${mode.name} -> fromString -> $mode', () {
        expect(HeatModeExtension.fromString(mode.name), mode);
      });
    }
  });
  // END_BLOCK_HEATMODE_ROUNDTRIP

  // START_BLOCK_USERTYPE_ROUNDTRIP
  group('UserType round-trip (scenario-2)', () {
    for (final type in UserType.values) {
      test('${type.name} -> fromString -> $type', () {
        expect(UserTypeExtension.fromString(type.name), type);
      });
    }
  });
  // END_BLOCK_USERTYPE_ROUNDTRIP

  // START_BLOCK_ORELSE_FALLBACK
  group('orElse fallback (scenario-3, scenario-4)', () {
    test('HeatMode "garbage" -> manual', () {
      expect(HeatModeExtension.fromString('garbage'), HeatMode.manual);
    });
    test('UserType "garbage" -> driver', () {
      expect(UserTypeExtension.fromString('garbage'), UserType.driver);
    });
    test('HeatMode пустая строка -> manual', () {
      expect(HeatModeExtension.fromString(''), HeatMode.manual);
    });
    test('case-sensitive: "Manual" != "manual" -> orElse manual', () {
      expect(HeatModeExtension.fromString('Manual'), HeatMode.manual);
    });
    test('case-sensitive: "Driver" != "driver" -> orElse driver', () {
      expect(UserTypeExtension.fromString('Driver'), UserType.driver);
    });
  });
  // END_BLOCK_ORELSE_FALLBACK

  // START_BLOCK_FORBIDDEN_MARKERS
  group('Forbidden markers', () {
    test('forbidden-1: fromString никогда не бросает', () {
      expect(() => HeatModeExtension.fromString('xxx'), returnsNormally);
      expect(() => UserTypeExtension.fromString('xxx'), returnsNormally);
    });
  });
  // END_BLOCK_FORBIDDEN_MARKERS

  // START_BLOCK_NAME_SNAPSHOT
  group('Regression snapshot имён enum (scenario-5, forbidden-2)', () {
    test('HeatMode.values.name == [manual, presets, auto]', () {
      expect(
        HeatMode.values.map((e) => e.name).toList(),
        const ['manual', 'presets', 'auto'],
      );
    });
    test('UserType.values.name == [driver, passenger]', () {
      expect(
        UserType.values.map((e) => e.name).toList(),
        const ['driver', 'passenger'],
      );
    });
  });
  // END_BLOCK_NAME_SNAPSHOT
}
