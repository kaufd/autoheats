import 'dart:async';
import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/constants/temperature_constants.dart';
import 'package:autoheat/src/services/hvac_service.dart';

class AutoHeatService {
  static final AutoHeatService _instance = AutoHeatService._internal();
  factory AutoHeatService() => _instance;
  AutoHeatService._internal();

  HvacService? _hvacService;
  double? _currentTemperature;

  final Map<UserType, Timer?> _heatTimers = {};

  final Map<UserType, Function(int)> _heatLevelCallbacks = {};

  void initialize(HvacService hvacService) {
    _hvacService = hvacService;
    _hvacService!.onCabinTemperatureChanged = (double temperature) {
      _currentTemperature = temperature;
      _updateAutoHeatForAllUsers();
    };
  }

  void setTemperature(double celsius) {
    _currentTemperature = celsius;
    _updateAutoHeatForAllUsers();
  }

  double? get currentTemperature => _currentTemperature;

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

    final sequence = TemperatureConstants.getHeatSequence(_currentTemperature!);
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
        _scheduleNextLevel(userType, 1, _getSequence()?.level2Duration ?? 0);
      } else if (nextLevel == 1) {
        callback(1);
        _scheduleNextLevel(userType, 0, _getSequence()?.level1Duration ?? 0);
      } else {
        callback(0);
      }
    });
  }

  HeatSequence? _getSequence() {
    if (_currentTemperature == null) return null;
    return TemperatureConstants.getHeatSequence(_currentTemperature!);
  }

  void dispose() {
    for (final timer in _heatTimers.values) {
      timer?.cancel();
    }
    _heatTimers.clear();
    _heatLevelCallbacks.clear();
  }
}
