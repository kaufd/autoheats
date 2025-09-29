import 'package:android_automotive_plugin/car/hvac_manager.dart';
import 'package:autoheat/src/models/temperature.dart';

class TemperatureSensorService {
  final CarHvacManager _carHvacManager;

  TemperatureSensorService(this._carHvacManager);

  Future<double> getCabinTemperature() async {
    try {
      final temperature = await _carHvacManager.getInsideTemperature();
      final celsius = temperature / 10.0;
      print('TemperatureSensorService: Получена температура салона: ${celsius}°C');
      return celsius;
    } catch (e) {
      print('TemperatureSensorService: Ошибка при получении температуры салона: $e');
      return 20.0;
    }
  }

  Future<TemperatureModel> getCabinTemperatureModel() async {
    final celsius = await getCabinTemperature();
    return TemperatureModel.now(celsius: celsius);
  }
}
