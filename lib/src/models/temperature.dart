class TemperatureModel {
  final double celsius;
  final DateTime timestamp;

  TemperatureModel({
    required this.celsius,
    required this.timestamp,
  });

  TemperatureModel.now({required this.celsius}) : timestamp = DateTime.now();
}
