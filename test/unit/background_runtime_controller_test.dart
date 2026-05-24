// FILE: test/unit/background_runtime_controller_test.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Unit-тесты BackgroundRuntimeController — lifecycle foreground-service без head-unit.
//   SCOPE: stopService command, ignition OFF shutdown, malformed events,
//          restart backoff retry/fail-stop.
//   DEPENDS: M-BACKGROUND, M-ENUMS
//   LINKS: V-M-BACKGROUND, FA-006
//   ROLE: TEST
//   MAP_MODE: LOCALS
// END_MODULE_CONTRACT

import 'dart:async';

import 'package:android_automotive_plugin/car/car_sensor_event.dart';
import 'package:android_automotive_plugin/car/car_sensor_types.dart';
import 'package:android_automotive_plugin/car/ignition_state.dart';
import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/services/background_runtime_controller.dart';
import 'package:fake_async/fake_async.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  // START_BLOCK_TEST_FAKES
  late FakeBackgroundServicePort servicePort;
  late FakeBackgroundModePort modePort;
  late BackgroundRuntimeController controller;

  setUp(() {
    servicePort = FakeBackgroundServicePort();
    modePort = FakeBackgroundModePort();
    controller = BackgroundRuntimeController(
      servicePort: servicePort,
      modePort: modePort,
      restartDelay: const Duration(seconds: 10),
      maxRestartAttempts: 3,
    );
  });

  tearDown(() async {
    await controller.dispose();
    await servicePort.dispose();
  });
  // END_BLOCK_TEST_FAKES

  // START_BLOCK_STOP_COMMAND
  test('scenario-1: stop command shuts down seats and stops service', () async {
    controller.registerStopHandler();

    servicePort.emit('stopService');
    await pumpEventQueue();

    expect(modePort.calls, [
      (userType: UserType.driver, level: 0),
      (userType: UserType.passenger, level: 0),
    ]);
    expect(servicePort.stopSelfCallCount, 1);
  });
  // END_BLOCK_STOP_COMMAND

  // START_BLOCK_IGNITION_OFF
  test('scenario-2: ignition OFF awaits driver/passenger shutdown', () async {
    modePort.useCompleters = true;
    var completed = false;

    final future = controller
        .handleIgnition(_ignitionEvent(IgnitionState.IGNITION_STATE_OFF))
        .then((_) => completed = true);
    await pumpEventQueue();

    expect(modePort.calls, [(userType: UserType.driver, level: 0)]);
    expect(completed, isFalse);

    modePort.complete(UserType.driver);
    await pumpEventQueue();
    expect(modePort.calls, [
      (userType: UserType.driver, level: 0),
      (userType: UserType.passenger, level: 0),
    ]);
    expect(completed, isFalse);

    modePort.complete(UserType.passenger);
    await future;
    expect(completed, isTrue);
  });
  // END_BLOCK_IGNITION_OFF

  // START_BLOCK_MALFORMED_IGNITION
  test('scenario-3: malformed ignition event is ignored', () async {
    await controller.handleIgnition(CarSensorEvent(
      CarSensorTypes.SENSOR_TYPE_IGNITION_STATE,
      0,
      const [],
      const [],
      const [],
    ));

    expect(modePort.calls, isEmpty);
    expect(servicePort.stopSelfCallCount, 0);
  });
  // END_BLOCK_MALFORMED_IGNITION

  // START_BLOCK_RESTART_BACKOFF
  test('scenario-4: start failure below max schedules retry', () {
    fakeAsync((async) {
      var retryCount = 0;

      controller.handleStartFailure(
        Exception('boom'),
        retry: () async {
          retryCount++;
        },
      );
      async.flushMicrotasks();

      expect(controller.restartAttempts, 1);
      expect(servicePort.notifications, [
        (title: 'AutoHeat Service', content: 'Перезапуск сервиса...'),
      ]);
      expect(retryCount, 0);

      async.elapse(const Duration(seconds: 10));
      async.flushMicrotasks();

      expect(retryCount, 1);
      expect(servicePort.stopSelfCallCount, 0);
    });
  });

  test('scenario-5: start failure at max attempts stops service', () {
    fakeAsync((async) {
      var retryCount = 0;
      controller.restartAttempts = 3;

      controller.handleStartFailure(
        Exception('boom'),
        retry: () async {
          retryCount++;
        },
      );
      async.flushMicrotasks();
      async.elapse(const Duration(seconds: 30));
      async.flushMicrotasks();

      expect(retryCount, 0);
      expect(servicePort.stopSelfCallCount, 1);
    });
  });
  // END_BLOCK_RESTART_BACKOFF
}

CarSensorEvent _ignitionEvent(int ignitionState) {
  return CarSensorEvent(
    CarSensorTypes.SENSOR_TYPE_IGNITION_STATE,
    0,
    const [],
    [ignitionState],
    const [],
  );
}

class FakeBackgroundServicePort implements BackgroundServicePort {
  final Map<String, StreamController<Map<String, dynamic>?>> _controllers = {};
  final List<({String title, String content})> notifications = [];
  int stopSelfCallCount = 0;

  @override
  Stream<Map<String, dynamic>?> on(String method) {
    return _controllers
        .putIfAbsent(
          method,
          () => StreamController<Map<String, dynamic>?>.broadcast(sync: true),
        )
        .stream;
  }

  void emit(String method, [Map<String, dynamic>? args]) {
    _controllers[method]?.add(args);
  }

  @override
  Future<void> setForegroundNotificationInfo({
    required String title,
    required String content,
  }) async {
    notifications.add((title: title, content: content));
  }

  @override
  Future<void> stopSelf() async {
    stopSelfCallCount++;
  }

  Future<void> dispose() async {
    for (final controller in _controllers.values) {
      await controller.close();
    }
  }
}

class FakeBackgroundModePort implements BackgroundModePort {
  final List<({UserType userType, int level})> calls = [];
  final Map<UserType, Completer<void>> _completers = {};
  bool useCompleters = false;

  @override
  Future<void> setHeatLevel(UserType userType, int level) {
    calls.add((userType: userType, level: level));
    if (!useCompleters) return Future.value();
    final completer = Completer<void>();
    _completers[userType] = completer;
    return completer.future;
  }

  void complete(UserType userType) {
    _completers[userType]?.complete();
  }
}
