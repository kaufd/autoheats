// FILE: lib/src/services/hvac_service.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Централизованная обёртка над AndroidAutomotivePlugin для UI,
//            AutoHeatService и CabinTemperatureCubit — единственная точка
//            доступа приложения к HVAC и температуре салона.
//   SCOPE: ленивый connect(), setSeatHeatLevel, getCabinTemperature,
//          конверсия (raw - 84) / 2, cached multi-listener события температуры.
//   DEPENDS: M-PLUGIN, M-ENUMS, M-LOGGER
//   LINKS: M-HVAC, V-M-HVAC, DF-SET-HEAT, DF-AUTO-HEAT, DF-INIT-TEMP, FA-003
//   ROLE: RUNTIME
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   CabinTemperatureListener - callback type для событий температуры салона
//   HvacService - singleton-обёртка плагина (регистрируется в GetIt)
//   HvacService() - конструктор: создаёт плагин и onHvacChangeEventCallback
//   initialize - ленивый connect(); идемпотентен через гард _isInitialized
//   isInitialized - геттер флага инициализации
//   androidAutomotivePlugin - геттер плагина (нужен background-изоляту)
//   setSeatHeatLevel(UserType, int) - запись уровня через CarHvacManager
//   getCabinTemperature - чтение температуры салона (°C); fallback 20.0 при ошибке
//   lastCabinTemperature - последняя опубликованная температура салона
//   addCabinTemperatureListener - подписать потребителя температуры, optional emitCurrent
//   removeCabinTemperatureListener - отписать потребителя температуры
//   _publishCabinTemperature - cache + fan-out listeners + listener error isolation
//   Logger markers - BLOCK_INITIALIZE, BLOCK_SET_SEAT_HEAT_LEVEL, BLOCK_HANDLE_TEMPERATURE_EVENT
//   _convertToCelsius - (raw - 84) / 2 -> °C
//   dispose - сброс _isInitialized и снятие onHvacChangeEventCallback/listeners
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.2.0 - Phase-4 Slice-3: multi-listener cabin temperature source]
//   PREVIOUS_CHANGE: [v1.1.0 - Phase-3: print() заменён на Logger с marker-anchors]
// END_CHANGE_SUMMARY

import 'package:android_automotive_plugin/android_automotive_plugin.dart';
import 'package:android_automotive_plugin/car/hvac_manager.dart';
import 'package:android_automotive_plugin/car/car_property_value.dart';
import 'package:android_automotive_plugin/car/hvac_property_ids.dart';
import 'package:android_automotive_plugin/car/vehicle_area_in_out_car.dart';
import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/utils/logger.dart';

typedef CabinTemperatureListener = void Function(double celsius);

class HvacService {
  late final AndroidAutomotivePlugin _androidAutomotivePlugin;
  late final CarHvacManager _hvacManager;
  bool _isInitialized = false;
  double? _lastCabinTemperature;
  final Set<CabinTemperatureListener> _cabinTemperatureListeners = {};

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
            _publishCabinTemperature(temperature);
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
  //   PURPOSE: Прочитать температуру салона, сконвертировать raw -> °C и опубликовать её.
  //   INPUTS: none
  //   OUTPUTS: { Future<double> - °C, fallback 20.0 при ошибке }
  //   SIDE_EFFECTS: Нативное чтение HVAC, cache/listener update, Logger marker BLOCK_GET_CABIN_TEMPERATURE.
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
      _publishCabinTemperature(celsius);
      return celsius;
    } catch (e) {
      const fallbackCelsius = 20.0;
      Logger.warn(
        'HvacService',
        'getCabinTemperature',
        'BLOCK_GET_CABIN_TEMPERATURE',
        'fallback',
        {'fallbackCelsius': fallbackCelsius, 'error': e},
      );
      _publishCabinTemperature(fallbackCelsius);
      return fallbackCelsius;
    }
    // END_BLOCK_GET_CABIN_TEMPERATURE
  }

  double? get lastCabinTemperature => _lastCabinTemperature;

  // START_CONTRACT: addCabinTemperatureListener
  //   PURPOSE: Подписать независимого потребителя температуры салона.
  //   INPUTS: { listener: CabinTemperatureListener, emitCurrent: bool }
  //   OUTPUTS: { void }
  //   SIDE_EFFECTS: Может синхронно вызвать listener при emitCurrent=true и cache!=null.
  //   LINKS: M-HVAC, M-CABIN-TEMPERATURE, M-AUTO-HEAT, V-M-HVAC, FA-003
  // END_CONTRACT: addCabinTemperatureListener
  void addCabinTemperatureListener(
    CabinTemperatureListener listener, {
    bool emitCurrent = false,
  }) {
    _cabinTemperatureListeners.add(listener);
    final current = _lastCabinTemperature;
    if (emitCurrent && current != null) {
      listener(current);
    }
  }

  // START_CONTRACT: removeCabinTemperatureListener
  //   PURPOSE: Отписать потребителя температуры салона.
  //   INPUTS: { listener: CabinTemperatureListener }
  //   OUTPUTS: { void }
  //   SIDE_EFFECTS: none
  //   LINKS: M-HVAC, M-CABIN-TEMPERATURE, M-AUTO-HEAT, V-M-HVAC, FA-003
  // END_CONTRACT: removeCabinTemperatureListener
  void removeCabinTemperatureListener(CabinTemperatureListener listener) {
    _cabinTemperatureListeners.remove(listener);
  }

  void _publishCabinTemperature(double celsius) {
    _lastCabinTemperature = celsius;
    for (final listener
        in List<CabinTemperatureListener>.of(_cabinTemperatureListeners)) {
      try {
        listener(celsius);
      } catch (e) {
        Logger.warn(
          'HvacService',
          'publishCabinTemperature',
          'BLOCK_HANDLE_TEMPERATURE_EVENT',
          'listener error ignored',
          {'error': e},
        );
      }
    }
  }

  double _convertToCelsius(int temperature) {
    return (temperature - 84) / 2;
  }

  void dispose() {
    _isInitialized = false;
    _lastCabinTemperature = null;
    _cabinTemperatureListeners.clear();
    _androidAutomotivePlugin.onHvacChangeEventCallback = null;
  }
}
