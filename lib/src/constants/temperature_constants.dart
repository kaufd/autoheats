// FILE: lib/src/constants/temperature_constants.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Маппинг температуры салона в TemperatureRange и HeatSequence —
//            источник расписаний авторежима и значений слайдера порога.
//   SCOPE: пороги диапазонов, расписания warm/cool/cold/freezing/extreme,
//          pure-функции getTemperatureRange и getHeatSequence.
//   DEPENDS: none
//   LINKS: M-CONSTANTS-TEMPERATURE, V-M-CONSTANTS-TEMPERATURE
//   ROLE: RUNTIME
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   TemperatureRange - enum off|warm|cool|cold|freezing|extreme
//   HeatSequence - длительности уровней 3/2/1 (минуты)
//   TemperatureConstants - контейнер порогов, расписаний и pure-функций
//   temperatureThresholds - нижние границы диапазонов (порядок итерации значим)
//   sliderMin / sliderMax / sliderDivisions - параметры слайдера порога
//   sliderValues / sliderLabels - значения и подписи слайдера [-5..15]
//   defaultTemperatureThreshold - 5.0, порог авторежима по умолчанию
//   temperatureSequences - HeatSequence по диапазонам (без off)
//   getTemperatureRange - °C -> TemperatureRange по temperatureThresholds
//   getHeatSequence - °C -> HeatSequence? (null при range.off)
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v0.2.0 - GRACE-инициализация: добавлены MODULE_CONTRACT и MODULE_MAP]
// END_CHANGE_SUMMARY

enum TemperatureRange {
  off, // > 10°C
  warm, // 5°C до 10°C
  cool, // 0°C до 5°C
  cold, // -5°C до 0°C
  freezing, // -10°C до -5°C
  extreme, // < -10°C
}

class HeatSequence {
  final int level3Duration;
  final int level2Duration;
  final int level1Duration;

  /// Температура, при которой уровень 3 → уровень 2 (досрочно, не дожидаясь таймера)
  final double level3StepDownCelsius;
  /// Температура, при которой уровень 2 → уровень 1
  final double level2StepDownCelsius;
  /// Температура, при которой уровень 1 → 0
  final double level1StepDownCelsius;

  const HeatSequence({
    required this.level3Duration,
    required this.level2Duration,
    required this.level1Duration,
    this.level3StepDownCelsius = double.negativeInfinity,
    this.level2StepDownCelsius = double.negativeInfinity,
    this.level1StepDownCelsius = double.negativeInfinity,
  });
}

class TemperatureConstants {
  static const Map<TemperatureRange, double> temperatureThresholds = {
    TemperatureRange.off: 10.0,
    TemperatureRange.warm: 5.0,
    TemperatureRange.cool: 0.0,
    TemperatureRange.cold: -5.0,
    TemperatureRange.freezing: -10.0,
    TemperatureRange.extreme: double.negativeInfinity,
  };

  static const double sliderMin = -5.0;
  static const double sliderMax = 15.0;
  static const int sliderDivisions = 4;

  static const List<double> sliderValues = [-5, 0, 5, 10, 15];
  static const List<String> sliderLabels = ['-5°C', '0°C', '5°C', '10°C', '15°C'];

  static const double defaultTemperatureThreshold = 5.0;

  static const Map<TemperatureRange, HeatSequence> temperatureSequences = {
    TemperatureRange.warm: HeatSequence(
      level3Duration: 3,
      level2Duration: 2,
      level1Duration: 5,
      level3StepDownCelsius: 6.0,
      level2StepDownCelsius: 8.0,
      level1StepDownCelsius: 11.0,
    ), // +5°C до +10°C: 10 мин
    TemperatureRange.cool: HeatSequence(
      level3Duration: 5,
      level2Duration: 3,
      level1Duration: 7,
      level3StepDownCelsius: 2.0,
      level2StepDownCelsius: 6.0,
      level1StepDownCelsius: 9.0,
    ), // 0°C до +5°C: 15 мин
    TemperatureRange.cold: HeatSequence(
      level3Duration: 8,
      level2Duration: 5,
      level1Duration: 7,
      level3StepDownCelsius: -2.0,
      level2StepDownCelsius: 3.0,
      level1StepDownCelsius: 7.0,
    ), // -5°C до 0°C: 20 мин
    TemperatureRange.freezing: HeatSequence(
      level3Duration: 12,
      level2Duration: 7,
      level1Duration: 7,
      level3StepDownCelsius: -7.0,
      level2StepDownCelsius: -2.0,
      level1StepDownCelsius: 4.0,
    ), // -10°C до -5°C: 26 мин
    TemperatureRange.extreme: HeatSequence(
      level3Duration: 15,
      level2Duration: 10,
      level1Duration: 8,
      level3StepDownCelsius: -12.0,
      level2StepDownCelsius: -7.0,
      level1StepDownCelsius: 0.0,
    ), // < -10°C: 33 мин
  };

  static TemperatureRange getTemperatureRange(double temperature) {
    for (final entry in temperatureThresholds.entries) {
      if (temperature >= entry.value) {
        return entry.key;
      }
    }
    return TemperatureRange.extreme;
  }

  static HeatSequence? getHeatSequence(double temperature) {
    final range = getTemperatureRange(temperature);
    if (range == TemperatureRange.off) return null;
    return temperatureSequences[range];
  }
}
