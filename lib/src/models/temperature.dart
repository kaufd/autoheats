class TemperatureModel {
  final double celsius;
  final DateTime timestamp;

  TemperatureModel({
    required this.celsius,
    required this.timestamp,
  });

  TemperatureModel.now({required this.celsius}) : timestamp = DateTime.now();

  @override
  String toString() => '${celsius.toStringAsFixed(1)}°C';
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

class AutoHeatConfig {
  static const Map<String, HeatSequence> temperatureSequences = {
    'warm': HeatSequence(
        level3Duration: 2, level2Duration: 2, level1Duration: 6), // +5°C до +10°C: 10 мин
    'cool': HeatSequence(
        level3Duration: 4, level2Duration: 2, level1Duration: 8), // 0°C до +5°C: 14 мин
    'cold': HeatSequence(
        level3Duration: 6, level2Duration: 4, level1Duration: 10), // -5°C до 0°C: 20 мин
    'freezing': HeatSequence(
        level3Duration: 8, level2Duration: 6, level1Duration: 12), // -10°C до -5°C: 26 мин
    'extreme':
        HeatSequence(level3Duration: 10, level2Duration: 8, level1Duration: 15), // < -10°C: 33 мин
  };

  static String getTemperatureRange(double temperature) {
    if (temperature > 10) return 'off';
    if (temperature >= 5) return 'warm';
    if (temperature >= 0) return 'cool';
    if (temperature >= -5) return 'cold';
    if (temperature >= -10) return 'freezing';
    return 'extreme';
  }

  static HeatSequence? getHeatSequence(double temperature) {
    final range = getTemperatureRange(temperature);
    if (range == 'off') return null;
    return temperatureSequences[range];
  }

  static int getDurationForLevel(int level, double temperature) {
    final sequence = getHeatSequence(temperature);
    if (sequence == null) return 0;

    switch (level) {
      case 3:
        return sequence.level3Duration;
      case 2:
        return sequence.level2Duration;
      case 1:
        return sequence.level1Duration;
      default:
        return 0;
    }
  }

  static String getTemperatureDescription(double temperature) {
    final range = getTemperatureRange(temperature);
    switch (range) {
      case 'off':
        return 'Тепло (>10°C) - подогрев выключен';
      case 'warm':
        return 'Умеренно (+5°C до +10°C) - 10 мин';
      case 'cool':
        return 'Прохладно (0°C до +5°C) - 14 мин';
      case 'cold':
        return 'Холодно (-5°C до 0°C) - 20 мин';
      case 'freezing':
        return 'Мороз (-10°C до -5°C) - 26 мин';
      case 'extreme':
        return 'Экстремальный холод (<-10°C) - 33 мин';
      default:
        return 'Неизвестный диапазон';
    }
  }

  static int getHeatLevelForTemperature(double temperature) {
    final range = getTemperatureRange(temperature);
    if (range == 'off') return 0;
    return 3;
  }
}
