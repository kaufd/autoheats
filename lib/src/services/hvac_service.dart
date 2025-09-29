import 'package:android_automotive_plugin/android_automotive_plugin.dart';
import 'package:android_automotive_plugin/car/hvac_manager.dart';
import 'package:android_automotive_plugin/car/car_property_value.dart';
import 'package:android_automotive_plugin/car/hvac_property_ids.dart';
import 'package:android_automotive_plugin/car/vehicle_area_in_out_car.dart';
import 'package:autoheat/src/app_enums.dart';

class HvacService {
  late final AndroidAutomotivePlugin _androidAutomotivePlugin;
  late final CarHvacManager _hvacManager;
  bool _isInitialized = false;
  Function(double)? onCabinTemperatureChanged;

  HvacService() {
    _androidAutomotivePlugin = AndroidAutomotivePlugin();
    _hvacManager = CarHvacManager(_androidAutomotivePlugin);

    _androidAutomotivePlugin.onHvacChangeEventCallback = (CarPropertyValue carPropertyValue) {
      try {
        if (carPropertyValue.propertyId == CarHvacPropertyIds.ID_HVAC_IN_OUT_TEMP) {
          final temperature = _convertToCelsius(carPropertyValue.value as int);

          if (carPropertyValue.areaId == VehicleAreaInOutCAR.InOutCAR_INSIDE) {
            onCabinTemperatureChanged?.call(temperature);
          }
        }
      } catch (e) {
        // ignore
      }
    };
  }

  Future<void> initialize() async {
    if (_isInitialized) return;

    try {
      await _androidAutomotivePlugin.connect();
      _isInitialized = true;
      print('HvacService: Сервис инициализирован');
    } catch (e) {
      print('HvacService: Ошибка инициализации: $e');
      rethrow;
    }
  }

  bool get isInitialized => _isInitialized;

  AndroidAutomotivePlugin get androidAutomotivePlugin => _androidAutomotivePlugin;

  Future<void> setSeatHeatLevel(UserType userType, int level) async {
    try {
      if (!isInitialized) {
        await initialize();
      }

      final isDriver = userType == UserType.driver;
      await _hvacManager.setSeatHeatLevel(isDriver, level);
      print(
          'HvacService: Установлен уровень подогрева $level для ${isDriver ? "водителя" : "пассажира"}');
    } catch (e) {
      print('HvacService: Ошибка при установке уровня подогрева: $e');
      rethrow;
    }
  }

  Future<double> getCabinTemperature() async {
    try {
      if (!isInitialized) {
        await initialize();
      }

      final temperature = await _hvacManager.getInsideTemperature();
      final celsius = _convertToCelsius(temperature);
      print('HvacService: Получена температура салона: ${celsius}°C');
      return celsius;
    } catch (e) {
      print('HvacService: Ошибка при получении температуры салона: $e');
      return 20.0;
    }
  }

  double _convertToCelsius(int temperature) {
    return (temperature - 84) / 2;
  }

  void dispose() {
    _isInitialized = false;
    _androidAutomotivePlugin.onHvacChangeEventCallback = null;
  }
}
