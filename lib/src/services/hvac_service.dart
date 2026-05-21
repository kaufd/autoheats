// FILE: lib/src/services/hvac_service.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Централизованная обёртка над AndroidAutomotivePlugin для UI и
//            AutoHeatService — единственная точка доступа приложения к HVAC.
//   SCOPE: ленивый connect(), setSeatHeatLevel, getCabinTemperature,
//          конверсия (raw - 84) / 2, проброс события температуры салона.
//   DEPENDS: M-PLUGIN, M-ENUMS
//   LINKS: M-HVAC, V-M-HVAC, DF-SET-HEAT, DF-AUTO-HEAT, DF-INIT-TEMP
//   ROLE: RUNTIME
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   HvacService - singleton-обёртка плагина (регистрируется в GetIt)
//   HvacService() - конструктор: создаёт плагин и onHvacChangeEventCallback
//   initialize - ленивый connect(); идемпотентен через гард _isInitialized
//   isInitialized - геттер флага инициализации
//   androidAutomotivePlugin - геттер плагина (нужен background-изоляту)
//   setSeatHeatLevel(UserType, int) - запись уровня через CarHvacManager
//   getCabinTemperature - чтение температуры салона (°C); fallback 20.0 при ошибке
//   onCabinTemperatureChanged - callback изменения температуры (единственный потребитель)
//   _convertToCelsius - (raw - 84) / 2 -> °C
//   dispose - сброс _isInitialized и снятие onHvacChangeEventCallback
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v0.2.0 - GRACE-инициализация: добавлены MODULE_CONTRACT и MODULE_MAP]
// END_CHANGE_SUMMARY

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
