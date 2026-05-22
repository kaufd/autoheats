// FILE: lib/src/services/hvac_service.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Централизованная обёртка над AndroidAutomotivePlugin для UI и
//            AutoHeatService — единственная точка доступа приложения к HVAC.
//   SCOPE: ленивый connect(), setSeatHeatLevel, getCabinTemperature,
//          конверсия (raw - 84) / 2, проброс события температуры салона.
//   DEPENDS: M-PLUGIN, M-ENUMS, M-LOGGER
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
//   Logger markers - BLOCK_INITIALIZE, BLOCK_SET_SEAT_HEAT_LEVEL, BLOCK_HANDLE_TEMPERATURE_EVENT
//   _convertToCelsius - (raw - 84) / 2 -> °C
//   dispose - сброс _isInitialized и снятие onHvacChangeEventCallback
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.1.0 - Phase-3: print() заменён на Logger с marker-anchors]
//   PREVIOUS_CHANGE: [v0.2.0 - GRACE-инициализация: добавлены MODULE_CONTRACT и MODULE_MAP]
// END_CHANGE_SUMMARY

import 'package:android_automotive_plugin/android_automotive_plugin.dart';
import 'package:android_automotive_plugin/car/hvac_manager.dart';
import 'package:android_automotive_plugin/car/car_property_value.dart';
import 'package:android_automotive_plugin/car/hvac_property_ids.dart';
import 'package:android_automotive_plugin/car/vehicle_area_in_out_car.dart';
import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/utils/logger.dart';

class HvacService {
  late final AndroidAutomotivePlugin _androidAutomotivePlugin;
  late final CarHvacManager _hvacManager;
  bool _isInitialized = false;
  Function(double)? onCabinTemperatureChanged;

  HvacService() {
    _androidAutomotivePlugin = AndroidAutomotivePlugin();
    _hvacManager = CarHvacManager(_androidAutomotivePlugin);

    _androidAutomotivePlugin.onHvacChangeEventCallback =
        (CarPropertyValue carPropertyValue) {
      // START_BLOCK_HANDLE_TEMPERATURE_EVENT
      try {
        if (carPropertyValue.propertyId ==
            CarHvacPropertyIds.ID_HVAC_IN_OUT_TEMP) {
          final temperature = _convertToCelsius(carPropertyValue.value as int);

          if (carPropertyValue.areaId == VehicleAreaInOutCAR.InOutCAR_INSIDE) {
            Logger.info(
              'HvacService',
              'onHvacChangeEvent',
              'BLOCK_HANDLE_TEMPERATURE_EVENT',
              'cabin temperature changed',
              {'celsius': temperature},
            );
            onCabinTemperatureChanged?.call(temperature);
          }
        }
      } catch (e) {
        Logger.warn(
          'HvacService',
          'onHvacChangeEvent',
          'BLOCK_HANDLE_TEMPERATURE_EVENT',
          'ignored malformed HVAC event',
          {'error': e},
        );
      }
      // END_BLOCK_HANDLE_TEMPERATURE_EVENT
    };
  }

  // START_CONTRACT: initialize
  //   PURPOSE: Лениво подключиться к AndroidAutomotivePlugin один раз.
  //   INPUTS: none
  //   OUTPUTS: { Future<void> }
  //   SIDE_EFFECTS: connect() в нативный слой, Logger marker BLOCK_INITIALIZE.
  //   LINKS: M-HVAC, M-PLUGIN, M-LOGGER, V-M-HVAC
  // END_CONTRACT: initialize
  Future<void> initialize() async {
    // START_BLOCK_INITIALIZE
    if (_isInitialized) return;

    try {
      await _androidAutomotivePlugin.connect();
      _isInitialized = true;
      Logger.info(
          'HvacService', 'initialize', 'BLOCK_INITIALIZE', 'initialized');
    } catch (e) {
      Logger.error(
        'HvacService',
        'initialize',
        'BLOCK_INITIALIZE',
        'error',
        {'error': e},
      );
      rethrow;
    }
    // END_BLOCK_INITIALIZE
  }

  bool get isInitialized => _isInitialized;

  AndroidAutomotivePlugin get androidAutomotivePlugin =>
      _androidAutomotivePlugin;

  // START_CONTRACT: setSeatHeatLevel
  //   PURPOSE: Записать уровень подогрева через CarHvacManager.
  //   INPUTS: { userType: UserType, level: int (0..3) }
  //   OUTPUTS: { Future<void> }
  //   SIDE_EFFECTS: Нативная HVAC-запись, Logger marker BLOCK_SET_SEAT_HEAT_LEVEL.
  //   LINKS: M-HVAC, M-PLUGIN, M-LOGGER, V-M-HVAC, DF-SET-HEAT
  // END_CONTRACT: setSeatHeatLevel
  Future<void> setSeatHeatLevel(UserType userType, int level) async {
    // START_BLOCK_SET_SEAT_HEAT_LEVEL
    try {
      if (!isInitialized) {
        await initialize();
      }

      final isDriver = userType == UserType.driver;
      await _hvacManager.setSeatHeatLevel(isDriver, level);
      Logger.info(
        'HvacService',
        'setSeatHeatLevel',
        'BLOCK_SET_SEAT_HEAT_LEVEL',
        'applied',
        {'level': level, 'userType': userType.name},
      );
    } catch (e) {
      Logger.error(
        'HvacService',
        'setSeatHeatLevel',
        'BLOCK_SET_SEAT_HEAT_LEVEL',
        'error',
        {'level': level, 'userType': userType.name, 'error': e},
      );
      rethrow;
    }
    // END_BLOCK_SET_SEAT_HEAT_LEVEL
  }

  // START_CONTRACT: getCabinTemperature
  //   PURPOSE: Прочитать температуру салона и сконвертировать raw -> °C.
  //   INPUTS: none
  //   OUTPUTS: { Future<double> - °C, fallback 20.0 при ошибке }
  //   SIDE_EFFECTS: Нативное чтение HVAC, Logger marker BLOCK_GET_CABIN_TEMPERATURE.
  //   LINKS: M-HVAC, M-PLUGIN, M-LOGGER, V-M-HVAC, DF-INIT-TEMP
  // END_CONTRACT: getCabinTemperature
  Future<double> getCabinTemperature() async {
    // START_BLOCK_GET_CABIN_TEMPERATURE
    try {
      if (!isInitialized) {
        await initialize();
      }

      final temperature = await _hvacManager.getInsideTemperature();
      final celsius = _convertToCelsius(temperature);
      Logger.info(
        'HvacService',
        'getCabinTemperature',
        'BLOCK_GET_CABIN_TEMPERATURE',
        'read',
        {'celsius': celsius},
      );
      return celsius;
    } catch (e) {
      Logger.warn(
        'HvacService',
        'getCabinTemperature',
        'BLOCK_GET_CABIN_TEMPERATURE',
        'fallback',
        {'fallbackCelsius': 20.0, 'error': e},
      );
      return 20.0;
    }
    // END_BLOCK_GET_CABIN_TEMPERATURE
  }

  double _convertToCelsius(int temperature) {
    return (temperature - 84) / 2;
  }

  void dispose() {
    _isInitialized = false;
    _androidAutomotivePlugin.onHvacChangeEventCallback = null;
  }
}
