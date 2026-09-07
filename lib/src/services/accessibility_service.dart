// FILE: lib/src/services/accessibility_service.dart
// VERSION: 1.1.0
// START_MODULE_CONTRACT
//   PURPOSE: Register the accessibility callback through the HvacService-owned automotive plugin.
//   SCOPE: initializeAccessibilityService and the entry-point callback that wakes the foreground service.
//   DEPENDS: M-HVAC, M-PLUGIN, M-BACKGROUND
//   LINKS: M-ACCESSIBILITY, V-M-ACCESSIBILITY
//   MAP_MODE: SUMMARY
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   initializeAccessibilityService - register the system callback handle
//   _accessibilityServiceCallback - start the foreground service when it is stopped
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.1.0 - Reuse HvacService.androidAutomotivePlugin instead of new instance
//                так MethodCallHandler не перезаписывает зарегистрированный HvacService handler]
// END_CHANGE_SUMMARY

import 'package:autoheat/src/di/service_locator.dart';
import 'package:autoheat/src/services/hvac_service.dart';
import 'package:flutter_background_service/flutter_background_service.dart';

Future<void> initializeAccessibilityService() async {
  // ВАЖНО: используем уже созданный в setupServiceLocator плагин.
  // Создание ещё одного AndroidAutomotivePlugin() перезапишет глобальный
  // MethodCallHandler канала 'android_automotive_plugin' (имя — const), и
  // HvacService.onHvacChangeEventCallback перестанет ловить cabin-temp events.
  final plugin = locator<HvacService>().androidAutomotivePlugin;
  plugin.setCallbackHandle(_accessibilityServiceCallback);
}

//
@pragma('vm:entry-point')
Future<void> _accessibilityServiceCallback() async {
  final service = FlutterBackgroundService();

  final isRunning = await service.isRunning();

  if (!isRunning) {
    service.startService();
  }
}
