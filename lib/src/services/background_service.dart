// FILE: lib/src/services/background_service.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Foreground-service flutter_background_service — живёт пока приложение
//            в фоне; слушает ignition, синхронизирует ModeCubit в своём изоляте.
//   SCOPE: конфигурация AndroidConfiguration, onStart entry-point, runtime-controller
//          для stopService, ignition ON/OFF, restart-backoff, остановка сервиса.
//   DEPENDS: M-PLUGIN, M-MODE, M-HVAC, M-DI, M-LOGGER
//   LINKS: M-BACKGROUND, V-M-BACKGROUND, DF-BACKGROUND, FA-006
//   ROLE: RUNTIME
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   initializeBackgroundService - конфигурация (channel my_foreground, id 888) + startService
//   onStart - @pragma('vm:entry-point'); ensureInitialized + setupServiceLocator + Logger marker
//   _startRuntimeConnection - plugin sensor callback + connect + automotive_connected flag
//   _retryRuntimeStart - bounded retry wrapper delegated from BackgroundRuntimeController
//   _modeCubit - ModeCubit, поднятый в background-изоляте
//   _runtimeController - BackgroundRuntimeController lifecycle owner
//   _maxRestartAttempts - retry limit для restart-backoff
//   stopBackgroundService - отправка stopService command в background isolate
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.2.0 - Phase-4 Slice-5: lifecycle вынесен в BackgroundRuntimeController]
//   PREVIOUS_CHANGE: [v1.1.0 - Phase-3: добавлены Logger markers foreground-service lifecycle]
// END_CHANGE_SUMMARY

import 'dart:async';
import 'dart:ui';

import 'package:android_automotive_plugin/android_automotive_plugin.dart';
import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/cubit/mode_cubit.dart';
import 'package:autoheat/src/di/service_locator.dart';
import 'package:autoheat/src/services/background_runtime_controller.dart';
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

ModeCubit? _modeCubit;
BackgroundRuntimeController? _runtimeController;
const int _maxRestartAttempts = 3;

@pragma('vm:entry-point')
onStart(ServiceInstance service) async {
  // START_BLOCK_ON_START
  BackgroundRuntimeController? runtimeController;
  AndroidAutomotivePlugin? plugin;

  try {
    DartPluginRegistrant.ensureInitialized();
    Logger.info('BackgroundService', 'onStart', 'BLOCK_ON_START', 'started');

    final servicePort = ServiceInstanceBackgroundServicePort(service);
    await servicePort.setForegroundNotificationInfo(
      title: 'AutoHeat Service',
      content: 'Мониторинг состояния автомобиля активен',
    );

    await setupServiceLocator();
    final modeCubit = locator<ModeCubit>();
    plugin = locator<HvacService>().androidAutomotivePlugin;

    _modeCubit = modeCubit;
    runtimeController = BackgroundRuntimeController(
      servicePort: servicePort,
      modePort: ModeCubitBackgroundModePort(modeCubit),
      maxRestartAttempts: _maxRestartAttempts,
    );
    _runtimeController = runtimeController;
    runtimeController.registerStopHandler();

    await _startRuntimeConnection(runtimeController, plugin);

    final completer = Completer<void>();
    Timer(Duration(seconds: 30), () {
      if (!completer.isCompleted) {
        completer.complete();
      }
    });

    await completer.future;
  } catch (e) {
    final controller = runtimeController;
    final retryPlugin = plugin;
    if (controller != null && retryPlugin != null) {
      await controller.handleStartFailure(
        e,
        retry: () => _retryRuntimeStart(controller, retryPlugin),
      );
    } else {
      Logger.error(
        'BackgroundService',
        'onStart',
        'BLOCK_ON_START',
        'failed before runtime controller was ready',
        {'error': e},
      );
      await service.stopSelf();
    }
  }
  // END_BLOCK_ON_START
}

Future<void> _startRuntimeConnection(
  BackgroundRuntimeController runtimeController,
  AndroidAutomotivePlugin plugin,
) async {
  plugin.onCarSensorEventCallback = runtimeController.handleIgnition;
  await plugin.connect();

  final prefs = await SharedPreferences.getInstance();
  await prefs.setBool('automotive_connected', true);

  runtimeController.markStarted();
}

Future<void> _retryRuntimeStart(
  BackgroundRuntimeController runtimeController,
  AndroidAutomotivePlugin plugin,
) async {
  try {
    await _startRuntimeConnection(runtimeController, plugin);
  } catch (e) {
    await runtimeController.handleStartFailure(
      e,
      retry: () => _retryRuntimeStart(runtimeController, plugin),
    );
  }
}

Future<void> stopBackgroundService() async {
  try {
    final modeCubit = _modeCubit;
    if (_runtimeController == null && modeCubit != null) {
      await modeCubit.setHeatLevel(UserType.driver, 0);
      await modeCubit.setHeatLevel(UserType.passenger, 0);
    }

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
