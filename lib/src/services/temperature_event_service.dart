import 'package:android_automotive_plugin/android_automotive_plugin.dart';
import 'package:android_automotive_plugin/car/car_property_value.dart';
import 'package:android_automotive_plugin/car/hvac_property_ids.dart';
import 'package:android_automotive_plugin/car/vehicle_area_in_out_car.dart';
import 'package:autoheat/src/models/temperature.dart';

class TemperatureEventService {
  final AndroidAutomotivePlugin _plugin;
  Function(TemperatureModel)? onCabinTemperatureChanged;

  TemperatureEventService(
    this._plugin, {
    this.onCabinTemperatureChanged,
  }) {
    _setupEventHandlers();
  }

  void _setupEventHandlers() {
    _plugin.onHvacChangeEventCallback = (CarPropertyValue carPropertyValue) {
      _handleHvacChangeEvent(carPropertyValue);
    };
  }

  void _handleHvacChangeEvent(CarPropertyValue carPropertyValue) {
    try {
      if (carPropertyValue.propertyId == CarHvacPropertyIds.ID_HVAC_IN_OUT_TEMP) {
        final temperature = (carPropertyValue.value as int) / 10.0;
        final temperatureModel = TemperatureModel.now(celsius: temperature);

        if (carPropertyValue.areaId == VehicleAreaInOutCAR.InOutCAR_INSIDE) {
          print('TemperatureEventService: Изменение температуры салона: ${temperature}°C');
          onCabinTemperatureChanged?.call(temperatureModel);
        }
      }
    } catch (e) {
      print('TemperatureEventService: Ошибка при обработке события изменения температуры: $e');
    }
  }

  void dispose() {
    _plugin.onHvacChangeEventCallback = null;
  }
}
