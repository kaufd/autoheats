import 'dart:async';
import 'dart:ui';

import 'package:android_automotive_plugin/android_automotive_plugin.dart';
import 'package:android_automotive_plugin/car/car_sensor_event.dart';
import 'package:android_automotive_plugin/car/car_sensor_types.dart';
import 'package:android_automotive_plugin/car/ignition_state.dart';
import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/cubit/mode_cubit.dart';
import 'package:autoheat/src/di/service_locator.dart';
import 'package:autoheat/src/services/hvac_service.dart';
import 'package:flutter_background_service/flutter_background_service.dart';
import 'package:shared_preferences/shared_preferences.dart';

Future<void> initializeBackgroundService() async {
  try {
    final service = FlutterBackgroundService();

    await service.configure(
      iosConfiguration: IosConfiguration(),
      androidConfiguration: AndroidConfiguration(
        onStart: onStart,
        //
        autoStart: true,
        autoStartOnBoot: true,
        isForegroundMode: true,
        //
        notificationChannelId: 'my_foreground',
        initialNotificationTitle: 'AutoHeat Service',
        initialNotificationContent: 'Сервис подогрева сидений активен',
        foregroundServiceNotificationId: 888,
      ),
    );

    final isRunning = await service.isRunning();

    if (!isRunning) {
      await service.startService();
    }
  } catch (e) {
    Timer(Duration(seconds: 5), () {
      initializeBackgroundService();
    });
  }
}

late AndroidAutomotivePlugin _androidAutomotivePlugin;
late ModeCubit _modeCubit;
bool _isServiceRunning = false;
int _restartAttempts = 0;
const int _maxRestartAttempts = 3;

@pragma('vm:entry-point')
onStart(ServiceInstance service) async {
  try {
    _isServiceRunning = true;
    _restartAttempts = 0;

    DartPluginRegistrant.ensureInitialized();

    if (service is AndroidServiceInstance) {
      service.setForegroundNotificationInfo(
        title: 'AutoHeat Service',
        content: 'Мониторинг состояния автомобиля активен',
      );
    }

    await setupServiceLocator();
    _modeCubit = locator<ModeCubit>();
    _androidAutomotivePlugin = locator<HvacService>().androidAutomotivePlugin;

    final completer = Completer();

    _androidAutomotivePlugin.onCarSensorEventCallback = _onCarSensorEvent;
    await _androidAutomotivePlugin.connect();

    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('automotive_connected', true);

    Timer(Duration(seconds: 30), () {
      if (!completer.isCompleted) {
        completer.complete();
      }
    });

    await completer.future;
  } catch (e) {
    _isServiceRunning = false;

    if (service is AndroidServiceInstance && _restartAttempts < _maxRestartAttempts) {
      _restartAttempts++;

      Timer(Duration(seconds: 10), () {
        if (!_isServiceRunning) {
          service.setForegroundNotificationInfo(
            title: 'AutoHeat Service',
            content: 'Перезапуск сервиса...',
          );
        }
      });
    } else if (_restartAttempts >= _maxRestartAttempts) {
      if (service is AndroidServiceInstance) {
        service.stopSelf();
      }
    }
  }
}

_onCarSensorEvent(CarSensorEvent carSensorEvent) async {
  try {
    if (carSensorEvent.sensorType == CarSensorTypes.SENSOR_TYPE_IGNITION_STATE) {
      int ignitionState = carSensorEvent.intValues.first;
      bool ignitionOn = ignitionState == IgnitionState.IGNITION_STATE_ON;

      if (ignitionOn) {
        // ModeCubit уже инициализирован и сам запустит нужную логику
        // на основе сохраненных настроек пользователей
      } else {
        _modeCubit.setHeatLevel(UserType.driver, 0);
        _modeCubit.setHeatLevel(UserType.passenger, 0);
      }
    }
  } catch (e) {
    // ignore
  }
}

Future<void> stopBackgroundService() async {
  try {
    _isServiceRunning = false;

    _modeCubit.setHeatLevel(UserType.driver, 0);
    _modeCubit.setHeatLevel(UserType.passenger, 0);

    final service = FlutterBackgroundService();
    service.invoke('stopService');
  } catch (e) {
    // ignore
  }
}
