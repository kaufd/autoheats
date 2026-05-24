// FILE: test/_helpers/fake_plugin.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Мок MethodChannel('android_automotive_plugin') для тестов HvacService.
//   SCOPE: перехват исходящих invokeMethod (connect/get/setHvacIntProperty) и
//          инъекция входящего onHvacChangeEvent через handlePlatformMessage.
//   DEPENDS: M-PLUGIN
//   LINKS: V-M-HVAC, V-M-PLUGIN
//   ROLE: TEST
//   MAP_MODE: LOCALS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   AutomotiveMethodChannelMock - мок канала плагина
//   install / uninstall - регистрация/снятие mock-обработчика исходящих вызовов
//   outgoingCalls / outgoingMethods - запись исходящих invokeMethod
//   hvacIntResponse - ответ мока на getHvacIntProperty
//   throwOnMethods - методы, на которых мок бросает PlatformException
//   emitHvacChangeEvent - инъекция входящего события датчика
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.1.0 - Phase-4 Slice-7: connect/HVAC write failures are awaited and testable]
// END_CHANGE_SUMMARY

import 'dart:convert';

import 'package:android_automotive_plugin/car/car_property_value.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

class AutomotiveMethodChannelMock {
  static const channelName = 'android_automotive_plugin';
  static const _channel = MethodChannel(channelName);
  static const _codec = StandardMethodCodec();

  /// Исходящие invokeMethod в порядке поступления.
  final List<MethodCall> outgoingCalls = [];

  /// Значение, которое мок возвращает на getHvacIntProperty.
  int hvacIntResponse = 0;

  /// Методы, на которых мок бросает PlatformException.
  /// Phase-4 Slice-7 сделал connect/setHvac*Property awaited, поэтому их
  /// ошибки теперь доступны HvacService как обычные awaited failures.
  final Set<String> throwOnMethods = {};

  List<String> get outgoingMethods =>
      outgoingCalls.map((c) => c.method).toList();

  TestDefaultBinaryMessenger get _messenger =>
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  void install() {
    _messenger.setMockMethodCallHandler(_channel, (call) async {
      outgoingCalls.add(call);
      if (throwOnMethods.contains(call.method)) {
        throw PlatformException(
            code: 'TEST_ERROR', message: 'mock throw: ${call.method}');
      }
      if (call.method == 'getHvacIntProperty') {
        return hvacIntResponse;
      }
      return null;
    });
  }

  void uninstall() {
    _messenger.setMockMethodCallHandler(_channel, null);
  }

  /// Имитировать входящее событие onHvacChangeEvent от нативного слоя:
  /// плагин принимает аргумент как JSON-строку CarPropertyValue.
  Future<void> emitHvacChangeEvent({
    required int propertyId,
    required int areaId,
    required Object? value,
  }) async {
    final cpv = CarPropertyValue(areaId, propertyId, 0, 0, value);
    final message = _codec.encodeMethodCall(
      MethodCall('onHvacChangeEvent', jsonEncode(cpv.toJson())),
    );
    await _messenger.handlePlatformMessage(channelName, message, (_) {});
  }
}
