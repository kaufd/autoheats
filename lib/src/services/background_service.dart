// FILE: lib/src/services/background_service.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Foreground-service flutter_background_service — живёт пока приложение
//            в фоне; слушает ignition, синхронизирует ModeCubit в своём изоляте.
//   SCOPE: конфигурация AndroidConfiguration, onStart entry-point, реакция на
//          ignition ON/OFF, restart-backoff, остановка сервиса.
//   DEPENDS: M-PLUGIN, M-MODE, M-HVAC, M-DI, M-LOGGER
//   LINKS: M-BACKGROUND, V-M-BACKGROUND, DF-BACKGROUND
//   ROLE: RUNTIME
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   initializeBackgroundService - конфигурация (channel my_foreground, id 888) + startService
//   onStart - @pragma('vm:entry-point'); ensureInitialized + setupServiceLocator + Logger marker
//   _androidAutomotivePlugin - локальный плагин background-изолята
//   _modeCubit - ModeCubit, поднятый в background-изоляте
//   _isServiceRunning / _restartAttempts / _maxRestartAttempts - состояние restart-backoff
//   _onCarSensorEvent - обработка ignition + Logger marker BLOCK_HANDLE_IGNITION
//   stopBackgroundService - сброс уровней и остановка сервиса
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.1.0 - Phase-3: добавлены Logger markers foreground-service lifecycle]
//   PREVIOUS_CHANGE: [v0.2.0 - GRACE-инициализация: добавлены MODULE_CONTRACT и MODULE_MAP]
// END_CHANGE_SUMMARY

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
import 'package:autoheat/src/utils/logger.dart';
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
    Logger.error(
      'BackgroundService',
      'initializeBackgroundService',
      'BLOCK_INITIALIZE_BACKGROUND_SERVICE',
      'configure failed; retry scheduled',
      {'error': e},
    );
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
  // START_BLOCK_ON_START
  try {
    _isServiceRunning = true;
    _restartAttempts = 0;

    DartPluginRegistrant.ensureInitialized();
    Logger.info('BackgroundService', 'onStart', 'BLOCK_ON_START', 'started');

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
    Logger.error(
      'BackgroundService',
      'onStart',
      'BLOCK_ON_START',
      'failed',
      {'error': e},
    );

    if (service is AndroidServiceInstance &&
        _restartAttempts < _maxRestartAttempts) {
      _restartAttempts++;
      Logger.warn(
        'BackgroundService',
        'onStart',
        'BLOCK_RESTART_BACKOFF',
        'restart scheduled',
        {'attempt': _restartAttempts, 'maxAttempts': _maxRestartAttempts},
      );

      Timer(Duration(seconds: 10), () {
        if (!_isServiceRunning) {
          service.setForegroundNotificationInfo(
            title: 'AutoHeat Service',
            content: 'Перезапуск сервиса...',
          );
        }
      });
    } else if (_restartAttempts >= _maxRestartAttempts) {
      Logger.error(
        'BackgroundService',
        'onStart',
        'BLOCK_RESTART_BACKOFF',
        'max attempts reached',
        {'attempt': _restartAttempts, 'maxAttempts': _maxRestartAttempts},
      );
      if (service is AndroidServiceInstance) {
        service.stopSelf();
      }
    }
  }
  // END_BLOCK_ON_START
}

_onCarSensorEvent(CarSensorEvent carSensorEvent) async {
  // START_BLOCK_HANDLE_IGNITION
  try {
    if (carSensorEvent.sensorType ==
        CarSensorTypes.SENSOR_TYPE_IGNITION_STATE) {
      int ignitionState = carSensorEvent.intValues.first;
      bool ignitionOn = ignitionState == IgnitionState.IGNITION_STATE_ON;
      Logger.info(
        'BackgroundService',
        'handleIgnition',
        'BLOCK_HANDLE_IGNITION',
        'ignition event',
        {'ignitionOn': ignitionOn, 'ignitionState': ignitionState},
      );

      if (ignitionOn) {
        // ModeCubit уже инициализирован и сам запустит нужную логику
        // на основе сохраненных настроек пользователей
      } else {
        _modeCubit.setHeatLevel(UserType.driver, 0);
        _modeCubit.setHeatLevel(UserType.passenger, 0);
      }
    }
  } catch (e) {
    Logger.warn(
      'BackgroundService',
      'handleIgnition',
      'BLOCK_HANDLE_IGNITION',
      'ignored malformed ignition event',
      {'error': e},
    );
  }
  // END_BLOCK_HANDLE_IGNITION
}

Future<void> stopBackgroundService() async {
  try {
    _isServiceRunning = false;

    _modeCubit.setHeatLevel(UserType.driver, 0);
    _modeCubit.setHeatLevel(UserType.passenger, 0);

    final service = FlutterBackgroundService();
    service.invoke('stopService');
  } catch (e) {
    Logger.warn(
      'BackgroundService',
      'stopBackgroundService',
      'BLOCK_STOP_BACKGROUND_SERVICE',
      'ignored stop failure',
      {'error': e},
    );
  }
}
