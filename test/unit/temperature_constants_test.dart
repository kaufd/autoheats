// FILE: test/unit/temperature_constants_test.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Unit-тесты pure-функций TemperatureConstants (V-M-CONSTANTS-TEMPERATURE).
//   SCOPE: getTemperatureRange на границах диапазонов, getHeatSequence, инвариант
//          сумм длительностей, порядок temperatureThresholds.
//   DEPENDS: M-CONSTANTS-TEMPERATURE
//   LINKS: V-M-CONSTANTS-TEMPERATURE
//   ROLE: TEST
//   MAP_MODE: LOCALS
// END_MODULE_CONTRACT

import 'package:autoheat/src/constants/temperature_constants.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  // START_BLOCK_GET_TEMPERATURE_RANGE
  group('getTemperatureRange — границы диапазонов (scenario-1)', () {
    const cases = <(double, TemperatureRange)>[
      (15.0, TemperatureRange.off),
      (10.0, TemperatureRange.off),
      (9.999, TemperatureRange.warm),
      (5.0, TemperatureRange.warm),
      (4.999, TemperatureRange.cool),
      (0.0, TemperatureRange.cool),
      (-0.001, TemperatureRange.cold),
      (-5.0, TemperatureRange.cold),
      (-5.001, TemperatureRange.freezing),
      (-10.0, TemperatureRange.freezing),
      (-10.001, TemperatureRange.extreme),
    ];
    for (final (input, expected) in cases) {
      test('$input -> $expected', () {
        expect(TemperatureConstants.getTemperatureRange(input), expected);
      });
    }
  });
  // END_BLOCK_GET_TEMPERATURE_RANGE

  // START_BLOCK_GET_HEAT_SEQUENCE
  group('getHeatSequence (scenario-2)', () {
    test('15.0 -> null (range off)', () {
      expect(TemperatureConstants.getHeatSequence(15.0), isNull);
    });
    test('10.0 -> null (граница off включена)', () {
      expect(TemperatureConstants.getHeatSequence(10.0), isNull);
    });
    test('7.0 -> warm (2, 2, 6)', () {
      final s = TemperatureConstants.getHeatSequence(7.0);
      expect(s, isNotNull);
      expect(s!.level3Duration, 3);
      expect(s.level2Duration, 2);
      expect(s.level1Duration, 5);
    });
    test('0.0 -> cool (5, 3, 7)', () {
      final s = TemperatureConstants.getHeatSequence(0.0);
      expect(s, isNotNull);
      expect(s!.level3Duration, 5);
      expect(s.level2Duration, 3);
      expect(s.level1Duration, 7);
    });
    test('-3.0 -> cold (8, 5, 7)', () {
      final s = TemperatureConstants.getHeatSequence(-3.0);
      expect(s, isNotNull);
      expect(s!.level3Duration, 8);
      expect(s.level2Duration, 5);
      expect(s.level1Duration, 7);
    });
    test('-7.0 -> freezing (12, 7, 7)', () {
      final s = TemperatureConstants.getHeatSequence(-7.0);
      expect(s, isNotNull);
      expect(s!.level3Duration, 12);
      expect(s.level2Duration, 7);
      expect(s.level1Duration, 7);
    });
    test('-15.0 -> extreme (15, 10, 8)', () {
      final s = TemperatureConstants.getHeatSequence(-15.0);
      expect(s, isNotNull);
      expect(s!.level3Duration, 15);
      expect(s.level2Duration, 10);
      expect(s.level1Duration, 8);
    });
  });
  // END_BLOCK_GET_HEAT_SEQUENCE

  // START_BLOCK_DURATION_SUM_INVARIANT
  group('Инвариант сумм длительностей (scenario-3)', () {
    int sum(HeatSequence s) =>
        s.level3Duration + s.level2Duration + s.level1Duration;

    test('cold == 20 минут', () {
      expect(sum(TemperatureConstants.getHeatSequence(-3.0)!), 20);
    });
    test('freezing == 26 минут', () {
      expect(sum(TemperatureConstants.getHeatSequence(-7.0)!), 26);
    });
    test('extreme == 33 минуты', () {
      expect(sum(TemperatureConstants.getHeatSequence(-15.0)!), 33);
    });
  });
  // END_BLOCK_DURATION_SUM_INVARIANT

  // START_BLOCK_FORBIDDEN_MARKERS
  group('Forbidden markers', () {
    test('forbidden-1: getHeatSequence на range off возвращает null', () {
      expect(TemperatureConstants.getHeatSequence(20.0), isNull);
    });
    test('forbidden-2: temperatureThresholds сохраняет порядок диапазонов', () {
      expect(
        TemperatureConstants.temperatureThresholds.keys.toList(),
        const [
          TemperatureRange.off,
          TemperatureRange.warm,
          TemperatureRange.cool,
          TemperatureRange.cold,
          TemperatureRange.freezing,
          TemperatureRange.extreme,
        ],
      );
    });
  });
  // END_BLOCK_FORBIDDEN_MARKERS
}
