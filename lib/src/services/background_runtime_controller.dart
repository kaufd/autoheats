// FILE: lib/src/services/background_runtime_controller.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Testable runtime-controller для foreground-service lifecycle.
//   SCOPE: stopService command, awaited seat shutdown, ignition OFF handling,
//          bounded restart-backoff, adapters for ServiceInstance and ModeCubit.
//   DEPENDS: M-BACKGROUND, M-MODE, M-ENUMS, M-LOGGER
//   LINKS: M-BACKGROUND, V-M-BACKGROUND, DF-BACKGROUND, FA-006
//   ROLE: RUNTIME
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   BackgroundServicePort - fake-friendly service API
//   ServiceInstanceBackgroundServicePort - adapter over ServiceInstance/AndroidServiceInstance
//   BackgroundModePort - fake-friendly heat-level command API
//   ModeCubitBackgroundModePort - adapter over ModeCubit
//   BackgroundRuntimeController - owns stop command, ignition OFF and retry policy
//   registerStopHandler - service.on('stopService') subscription
//   stopService - shutdown seats then stopSelf
//   shutdownSeats - sequential driver/passenger setHeatLevel(0), per-seat failure isolation
//   handleIgnition - ignition OFF -> shutdownSeats
//   handleStartFailure - bounded restart-backoff/fail-stop
//   markStarted - reset runtime running state after successful start
//   dispose - cancel stop subscription and retry timer
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.0.0 - Phase-4 Slice-5: выделен BackgroundRuntimeController]
// END_CHANGE_SUMMARY

import 'dart:async';

import 'package:android_automotive_plugin/car/car_sensor_event.dart';
import 'package:android_automotive_plugin/car/car_sensor_types.dart';
import 'package:android_automotive_plugin/car/ignition_state.dart';
import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/cubit/mode_cubit.dart';
import 'package:autoheat/src/utils/logger.dart';
import 'package:flutter_background_service/flutter_background_service.dart';

abstract class BackgroundServicePort {
  Stream<Map<String, dynamic>?> on(String method);

  Future<void> stopSelf();

  Future<void> setForegroundNotificationInfo({
    required String title,
    required String content,
  });
}

class ServiceInstanceBackgroundServicePort implements BackgroundServicePort {
  final ServiceInstance _service;

  ServiceInstanceBackgroundServicePort(this._service);

  @override
  Stream<Map<String, dynamic>?> on(String method) => _service.on(method);

  @override
  Future<void> stopSelf() => _service.stopSelf();

  @override
  Future<void> setForegroundNotificationInfo({
    required String title,
    required String content,
  }) async {
    final service = _service;
    if (service is AndroidServiceInstance) {
      await service.setForegroundNotificationInfo(
        title: title,
        content: content,
      );
    }
  }
}

abstract class BackgroundModePort {
  Future<void> setHeatLevel(UserType userType, int level);
}

class ModeCubitBackgroundModePort implements BackgroundModePort {
  final ModeCubit _modeCubit;

  ModeCubitBackgroundModePort(this._modeCubit);

  @override
  Future<void> setHeatLevel(UserType userType, int level) {
    return _modeCubit.setHeatLevel(userType, level);
  }
}

class BackgroundRuntimeController {
  final BackgroundServicePort servicePort;
  final BackgroundModePort modePort;
  final Duration restartDelay;
  final int maxRestartAttempts;

  StreamSubscription<Map<String, dynamic>?>? _stopSubscription;
  Timer? _retryTimer;

  bool isServiceRunning = false;
  int restartAttempts = 0;

  BackgroundRuntimeController({
    required this.servicePort,
    required this.modePort,
    this.restartDelay = const Duration(seconds: 10),
    this.maxRestartAttempts = 3,
  });

  // START_CONTRACT: registerStopHandler
  //   PURPOSE: Подписаться на command stopService из UI/main isolate.
  //   INPUTS: none
  //   OUTPUTS: { void }
  //   SIDE_EFFECTS: Stream subscription; stop command triggers stopService().
  //   LINKS: M-BACKGROUND, V-M-BACKGROUND, FA-006
  // END_CONTRACT: registerStopHandler
  void registerStopHandler() {
    _stopSubscription?.cancel();
    _stopSubscription = servicePort.on('stopService').listen((_) {
      unawaited(stopService());
    });
  }

  // START_CONTRACT: markStarted
  //   PURPOSE: Зафиксировать успешный runtime start и сбросить retry counter.
  //   INPUTS: none
  //   OUTPUTS: { void }
  //   SIDE_EFFECTS: state mutation.
  //   LINKS: M-BACKGROUND, V-M-BACKGROUND, FA-006
  // END_CONTRACT: markStarted
  void markStarted() {
    isServiceRunning = true;
    restartAttempts = 0;
  }

  // START_CONTRACT: stopService
  //   PURPOSE: Остановить background runtime: выключить сиденья и вызвать stopSelf.
  //   INPUTS: none
  //   OUTPUTS: { Future<void> }
  //   SIDE_EFFECTS: HVAC commands through ModeCubit, ServiceInstance.stopSelf.
  //   LINKS: M-BACKGROUND, M-MODE, V-M-BACKGROUND, FA-006
  // END_CONTRACT: stopService
  Future<void> stopService() async {
    isServiceRunning = false;
    await shutdownSeats(trigger: 'stopService');
    await servicePort.stopSelf();
  }

  // START_CONTRACT: shutdownSeats
  //   PURPOSE: Последовательно отправить driver/passenger level=0, не прерывая второй seat при ошибке первого.
  //   INPUTS: { trigger: String }
  //   OUTPUTS: { Future<void> }
  //   SIDE_EFFECTS: ModeCubit.setHeatLevel calls, Logger warning per failed seat.
  //   LINKS: M-BACKGROUND, M-MODE, V-M-BACKGROUND, FA-006
  // END_CONTRACT: shutdownSeats
  Future<void> shutdownSeats({required String trigger}) async {
    for (final userType in [UserType.driver, UserType.passenger]) {
      try {
        await modePort.setHeatLevel(userType, 0);
      } catch (e) {
        Logger.warn(
          'BackgroundService',
          'shutdownSeats',
          'BLOCK_HANDLE_IGNITION',
          'seat shutdown failed',
          {'trigger': trigger, 'userType': userType.name, 'error': e},
        );
      }
    }
  }

  // START_CONTRACT: handleIgnition
  //   PURPOSE: Обработать ignition event; OFF выключает оба сиденья с await.
  //   INPUTS: { carSensorEvent: CarSensorEvent }
  //   OUTPUTS: { Future<void> }
  //   SIDE_EFFECTS: Может вызвать shutdownSeats, Logger marker BLOCK_HANDLE_IGNITION.
  //   LINKS: M-BACKGROUND, M-MODE, V-M-BACKGROUND, FA-006
  // END_CONTRACT: handleIgnition
  Future<void> handleIgnition(CarSensorEvent carSensorEvent) async {
    // START_BLOCK_HANDLE_IGNITION
    try {
      if (carSensorEvent.sensorType !=
          CarSensorTypes.SENSOR_TYPE_IGNITION_STATE) {
        return;
      }

      final ignitionState = carSensorEvent.intValues.first;
      final ignitionOn = ignitionState == IgnitionState.IGNITION_STATE_ON;
      Logger.info(
        'BackgroundService',
        'handleIgnition',
        'BLOCK_HANDLE_IGNITION',
        'ignition event',
        {'ignitionOn': ignitionOn, 'ignitionState': ignitionState},
      );

      if (!ignitionOn) {
        await shutdownSeats(trigger: 'ignitionOff');
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

  // START_CONTRACT: handleStartFailure
  //   PURPOSE: Bounded restart-backoff или fail-stop после startup failure.
  //   INPUTS: { error: Object, retry: Future<void> Function() }
  //   OUTPUTS: { Future<void> }
  //   SIDE_EFFECTS: notification update, Timer retry, stopSelf at max attempts.
  //   LINKS: M-BACKGROUND, V-M-BACKGROUND, FA-006
  // END_CONTRACT: handleStartFailure
  Future<void> handleStartFailure(
    Object error, {
    required Future<void> Function() retry,
  }) async {
    isServiceRunning = false;
    Logger.error(
      'BackgroundService',
      'onStart',
      'BLOCK_ON_START',
      'failed',
      {'error': error},
    );

    if (restartAttempts >= maxRestartAttempts) {
      Logger.error(
        'BackgroundService',
        'onStart',
        'BLOCK_RESTART_BACKOFF',
        'max attempts reached',
        {'attempt': restartAttempts, 'maxAttempts': maxRestartAttempts},
      );
      await servicePort.stopSelf();
      return;
    }

    restartAttempts++;
    Logger.warn(
      'BackgroundService',
      'onStart',
      'BLOCK_RESTART_BACKOFF',
      'restart scheduled',
      {'attempt': restartAttempts, 'maxAttempts': maxRestartAttempts},
    );

    await servicePort.setForegroundNotificationInfo(
      title: 'AutoHeat Service',
      content: 'Перезапуск сервиса...',
    );

    _retryTimer?.cancel();
    _retryTimer = Timer(restartDelay, () {
      if (!isServiceRunning) {
        unawaited(retry());
      }
    });
  }

  Future<void> dispose() async {
    _retryTimer?.cancel();
    _retryTimer = null;
    await _stopSubscription?.cancel();
    _stopSubscription = null;
  }
}
