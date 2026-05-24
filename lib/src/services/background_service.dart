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
//   _startRuntimeConnection - sensor callback + HvacService.initialize (in-flight guard) + automotive_connected flag
//   _retryRuntimeStart - bounded retry wrapper delegated from BackgroundRuntimeController
//   _maxRestartAttempts - retry limit для restart-backoff
//   stopBackgroundService - отправка stopService command в background isolate
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.3.0 - Use HvacService.initialize() (in-flight guarded) вместо прямого plugin.connect(), удалён dead-code seat-shutdown в UI-изоляте]
//   PREVIOUS_CHANGE: [v1.2.0 - Phase-4 Slice-5: lifecycle вынесен в BackgroundRuntimeController]
// END_CHANGE_SUMMARY

import 'dart:async';
import 'dart:ui';

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

const int _maxRestartAttempts = 3;

@pragma('vm:entry-point')
onStart(ServiceInstance service) async {
  // START_BLOCK_ON_START
  BackgroundRuntimeController? runtimeController;
  HvacService? hvacService;

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
    hvacService = locator<HvacService>();

    runtimeController = BackgroundRuntimeController(
      servicePort: servicePort,
      modePort: ModeCubitBackgroundModePort(modeCubit),
      maxRestartAttempts: _maxRestartAttempts,
    );
    runtimeController.registerStopHandler();

    await _startRuntimeConnection(runtimeController, hvacService);

    final completer = Completer<void>();
    Timer(Duration(seconds: 30), () {
      if (!completer.isCompleted) {
        completer.complete();
      }
    });

    await completer.future;
  } catch (e) {
    final controller = runtimeController;
    final retryHvac = hvacService;
    if (controller != null && retryHvac != null) {
      await controller.handleStartFailure(
        e,
        retry: () => _retryRuntimeStart(controller, retryHvac),
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

// _startRuntimeConnection идёт через HvacService.initialize() (с in-flight guard),
// а не вызывает plugin.connect() напрямую: иначе параллельный путь через
// ModeCubit._initialize → seedCurrentTemperatureFromHvac → HvacService.initialize
// мог бы выстрелить вторым concurrent connect на тот же AndroidAutomotivePlugin.
Future<void> _startRuntimeConnection(
  BackgroundRuntimeController runtimeController,
  HvacService hvacService,
) async {
  hvacService.androidAutomotivePlugin.onCarSensorEventCallback =
      runtimeController.handleIgnition;
  await hvacService.initialize();

  final prefs = await SharedPreferences.getInstance();
  await prefs.setBool('automotive_connected', true);

  runtimeController.markStarted();
}

Future<void> _retryRuntimeStart(
  BackgroundRuntimeController runtimeController,
  HvacService hvacService,
) async {
  try {
    await _startRuntimeConnection(runtimeController, hvacService);
  } catch (e) {
    await runtimeController.handleStartFailure(
      e,
      retry: () => _retryRuntimeStart(runtimeController, hvacService),
    );
  }
}

Future<void> stopBackgroundService() async {
  // _modeCubit / _runtimeController присваиваются ТОЛЬКО в onStart, который
  // выполняется в background-изоляте. Из UI-изолята эти top-level переменные
  // всегда null, поэтому никакого "shutdown seats fallback" здесь делать нельзя.
  // Просто шлём команду stopService — её ловит registerStopHandler в фоне и сам
  // вызывает runtimeController.stopService() → shutdownSeats → stopSelf.
  try {
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
