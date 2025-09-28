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

  const HeatSequence({
    required this.level3Duration,
    required this.level2Duration,
    required this.level1Duration,
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
      level3Duration: 2,
      level2Duration: 2,
      level1Duration: 6,
    ), // +5°C до +10°C: 10 мин
    TemperatureRange.cool: HeatSequence(
      level3Duration: 4,
      level2Duration: 2,
      level1Duration: 8,
    ), // 0°C до +5°C: 14 мин
    TemperatureRange.cold: HeatSequence(
      level3Duration: 6,
      level2Duration: 4,
      level1Duration: 10,
    ), // -5°C до 0°C: 20 мин
    TemperatureRange.freezing: HeatSequence(
      level3Duration: 8,
      level2Duration: 6,
      level1Duration: 12,
    ), // -10°C до -5°C: 26 мин
    TemperatureRange.extreme: HeatSequence(
      level3Duration: 10,
      level2Duration: 8,
      level1Duration: 15,
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
