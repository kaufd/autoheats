import 'dart:async';
import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/models/temperature.dart';

class AutoHeatService {
  static final AutoHeatService _instance = AutoHeatService._internal();
  factory AutoHeatService() => _instance;
  AutoHeatService._internal();

  TemperatureModel? _currentTemperature;

  final Map<UserType, Timer?> _heatTimers = {};

  final Map<UserType, Function(int)> _heatLevelCallbacks = {};

  void setTemperature(double celsius) {
    _currentTemperature = TemperatureModel.now(celsius: celsius);
    _updateAutoHeatForAllUsers();
  }

  TemperatureModel? get currentTemperature => _currentTemperature;

  void startAutoHeat(UserType userType, Function(int) onHeatLevelChanged) {
    _heatLevelCallbacks[userType] = onHeatLevelChanged;
    _updateAutoHeatForUser(userType);
  }

  void stopAutoHeat(UserType userType) {
    _heatTimers[userType]?.cancel();
    _heatTimers[userType] = null;
    _heatLevelCallbacks.remove(userType);
  }

  void _updateAutoHeatForAllUsers() {
    for (final userType in _heatLevelCallbacks.keys) {
      _updateAutoHeatForUser(userType);
    }
  }

  void _updateAutoHeatForUser(UserType userType) {
    if (_currentTemperature == null) return;

    final callback = _heatLevelCallbacks[userType];
    if (callback == null) return;

    _heatTimers[userType]?.cancel();

    final sequence = AutoHeatConfig.getHeatSequence(_currentTemperature!.celsius);
    if (sequence == null) {
      callback(0);
      return;
    }

    _startHeatSequence(userType, sequence);
  }

  void _startHeatSequence(UserType userType, HeatSequence sequence) {
    final callback = _heatLevelCallbacks[userType];
    if (callback == null) return;

    callback(3);
    _scheduleNextLevel(userType, 2, sequence.level3Duration);
  }

  void _scheduleNextLevel(UserType userType, int nextLevel, int currentDuration) {
    if (currentDuration == 0) return;

    _heatTimers[userType] = Timer(Duration(minutes: currentDuration), () {
      final callback = _heatLevelCallbacks[userType];
      if (callback == null) return;

      if (nextLevel == 2) {
        callback(2);
        final sequence = AutoHeatConfig.getHeatSequence(_currentTemperature!.celsius);
        if (sequence != null) {
          _scheduleNextLevel(userType, 1, sequence.level2Duration);
        }
      } else if (nextLevel == 1) {
        callback(1);
        final sequence = AutoHeatConfig.getHeatSequence(_currentTemperature!.celsius);
        if (sequence != null) {
          _scheduleNextLevel(userType, 0, sequence.level1Duration);
        }
      } else {
        callback(0);
      }
    });
  }

  void dispose() {
    for (final timer in _heatTimers.values) {
      timer?.cancel();
    }
    _heatTimers.clear();
    _heatLevelCallbacks.clear();
  }
}
