// FILE: packages/android_automotive_plugin/lib/android_automotive_plugin.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Local snapshot/fork Flutter bridge to Android Automotive car APIs.
//   SCOPE: MethodChannel calls, HVAC/sensor callbacks, accessibility callback handle.
//   DEPENDS: flutter/services, android_automotive_plugin/car/*
//   LINKS: M-PLUGIN, V-M-PLUGIN, FA-007
//   ROLE: RUNTIME
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   AndroidAutomotivePlugin - MethodChannel facade and native callback dispatcher
//   connect - awaited native connect; propagates channel errors
//   setHvacIntProperty - awaited HVAC int write; propagates channel errors
//   getHvacIntProperty - awaited HVAC int read
//   setHvacFloatProperty - awaited HVAC float write; propagates channel errors
//   getHvacFloatProperty - awaited HVAC float read
//   setCallbackHandle - register background callback handle
//   entrypoint - Android accessibility/background callback entry point
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.1.0 - Phase-4 Slice-7: local patch awaits connect/HVAC writes so errors propagate]
// END_CHANGE_SUMMARY

import 'dart:convert';
import 'dart:ui';

import 'package:android_automotive_plugin/car/car_property_value.dart';
import 'package:android_automotive_plugin/car/car_sensor_event.dart';
import 'package:android_automotive_plugin/car/vehicle_property_ids.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class AndroidAutomotivePlugin {
  @visibleForTesting
  final methodChannel = const MethodChannel('android_automotive_plugin');

  Function(CarSensorEvent)? onCarSensorEventCallback;
  Function(CarPropertyValue)? onHvacChangeEventCallback;
  Function(CarPropertyValue)? onCarVendorExtensionCallback;
  Function(CarPropertyValue)? onCarClusterInteractionEventCallback;
  Function(String)? onLogCallback;

  AndroidAutomotivePlugin() {
    methodChannel.setMethodCallHandler((call) async {
      if (call.method == "onCarSensorEvent") {
        final CarSensorEvent carSensorEvent =
            CarSensorEvent.fromJson(jsonDecode(call.arguments));

        if (onCarSensorEventCallback != null) {
          onCarSensorEventCallback!(carSensorEvent);
        }

        if (onLogCallback != null) {
          onLogCallback!(
              "${call.method}: ${VehiclePropertyIds.valueToString(carSensorEvent.sensorType)}, value:${carSensorEvent.intValues[0]}");
        }
      }
      //
      else if (call.method == "onHvacChangeEvent") {
        final CarPropertyValue carPropertyValue =
            CarPropertyValue.fromJson(jsonDecode(call.arguments));

        if (onHvacChangeEventCallback != null) {
          onHvacChangeEventCallback!(carPropertyValue);
        }

        if (onLogCallback != null) {
          onLogCallback!(
              "${call.method}: ${VehiclePropertyIds.valueToString(carPropertyValue.propertyId)}, area:${carPropertyValue.areaId}, value:${carPropertyValue.value}");
        }
      }
      //
      else if (call.method == "onCarVendorExtensionCallback") {
        final CarPropertyValue carPropertyValue =
            CarPropertyValue.fromJson(jsonDecode(call.arguments));

        if (onCarVendorExtensionCallback != null) {
          onCarVendorExtensionCallback!(carPropertyValue);
        }

        // if (onLogCallback != null) {
        //   onLogCallback!(
        //       "${call.method}: ${VehiclePropertyIds.valueToString(carPropertyValue.propertyId)}, area:${carPropertyValue.areaId}, value:${carPropertyValue.value}");
        // }
      }
      //
      else if (call.method == "onCarClusterInteractionEvent") {
        final CarPropertyValue carPropertyValue =
            CarPropertyValue.fromJson(jsonDecode(call.arguments));

        if (onCarClusterInteractionEventCallback != null) {
          onCarClusterInteractionEventCallback!(carPropertyValue);
        }

        if (onLogCallback != null) {
          onLogCallback!(
              "${call.method}: ${VehiclePropertyIds.valueToString(carPropertyValue.propertyId)}, area:${carPropertyValue.areaId}, value:${carPropertyValue.value}");
        }
      }
      //
      else if (call.method == "onLogEvent") {
        if (onLogCallback != null) {
          onLogCallback!("${call.method}: ${call.arguments as String}");
        }
      }
      //
      else if (call.method == "onCrash") {
        if (onLogCallback != null) {
          onLogCallback!("${call.method}: ${call.arguments as String}");
        }
      }
    });
  }

  Future<void> connect() async {
    await methodChannel.invokeMethod("connect");
  }

  Future<void> setHvacIntProperty(int propertyId, int area, int value) async {
    await methodChannel.invokeMethod("setHvacIntProperty", {
      "propertyId": propertyId,
      "area": area,
      "value": value,
    });
  }

  Future<int> getHvacIntProperty(int propertyId, int area) async {
    final value = await methodChannel.invokeMethod("getHvacIntProperty", {
      "propertyId": propertyId,
      "area": area,
    });

    return value;
  }

  Future<void> setHvacFloatProperty(
      int propertyId, int area, double value) async {
    await methodChannel.invokeMethod("setHvacFloatProperty", {
      "propertyId": propertyId,
      "area": area,
      "value": value,
    });
  }

  Future<double> getHvacFloatProperty(int propertyId, int area) async {
    final value = await methodChannel.invokeMethod("getHvacFloatProperty", {
      "propertyId": propertyId,
      "area": area,
    });

    return value;
  }

  Future<void> setCallbackHandle(Function() callback) async {
    final handle = PluginUtilities.getCallbackHandle(callback);
    if (handle != null) {
      await methodChannel
          .invokeMethod("setAccessibilityServiceCallbackHandler", {
        "handleId": handle.toRawHandle(),
      });
    }
  }

  ///////

  Future<CarSensorEvent> getLatestSensorEvent(int sensorType) async {
    final value = await methodChannel.invokeMethod("getLatestSensorEvent", {
      "sensorType": sensorType,
    });

    final CarSensorEvent carSensorEvent =
        CarSensorEvent.fromJson(jsonDecode(value));

    return carSensorEvent;
  }

  Future<bool> setVehicleSettingMusicAlbumPictureFilePath(String path) async {
    final value = await methodChannel
        .invokeMethod("setVehicleSettingMusicAlbumPictureFilePath", {
      "path": path,
    });

    return value;
  }

  Future<void> setDoubleMediaMusicAlbumPictureFilePath({
    required int doublePlayingId,
    required String songId,
    required String path,
  }) async {
    await methodChannel
        .invokeMethod("setDoubleMediaMusicAlbumPictureFilePath", {
      "doublePlayingId": doublePlayingId,
      "songId": songId,
      "path": path,
    });
  }

  Future<void> setDoubleMediaMusicSource({
    required int playingId,
    required String programName,
    required String singerName,
    required String songName,
    required int sourceType,
  }) async {
    await methodChannel.invokeMethod("setDoubleMediaMusicSource", {
      "playingId": playingId,
      "programName": programName,
      "singerName": singerName,
      "songName": songName,
      "sourceType": sourceType,
    });
  }

  Future<void> resetDoubleMediaPicture({
    required int playingId,
  }) async {
    await methodChannel.invokeMethod("resetDoubleMediaPicture", {
      "playingId": playingId,
    });
  }

  Future<void> setNaviSurface() async {
    await methodChannel.invokeMethod("setNaviSurface");
  }
}

///////

@pragma('vm:entry-point')
Future<void> entrypoint(List<String> args) async {
  WidgetsFlutterBinding.ensureInitialized();

  final int handle = int.parse(args.first);
  final callbackHandle = CallbackHandle.fromRawHandle(handle);
  final callback = PluginUtilities.getCallbackFromHandle(callbackHandle);
  if (callback != null) {
    callback();
  }
}
