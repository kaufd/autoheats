import 'dart:async';
import 'package:flutter_background_service/flutter_background_service.dart';

class ServiceStatus {
  static final ServiceStatus _instance = ServiceStatus._internal();
  factory ServiceStatus() => _instance;
  ServiceStatus._internal();

  bool _isServiceRunning = false;
  bool _isServiceInitialized = false;
  Timer? _statusCheckTimer;

  bool get isServiceRunning => _isServiceRunning;
  bool get isServiceInitialized => _isServiceInitialized;

  /// Инициализация проверки статуса сервиса
  Future<void> initialize() async {
    if (_isServiceInitialized) return;

    try {
      final service = FlutterBackgroundService();
      _isServiceRunning = await service.isRunning();
      _isServiceInitialized = true;

      // Запускаем периодическую проверку статуса
      _startStatusCheck();

      print('ServiceStatus инициализирован. Сервис запущен: $_isServiceRunning');
    } catch (e) {
      print('Ошибка инициализации ServiceStatus: $e');
    }
  }

  /// Запуск периодической проверки статуса
  void _startStatusCheck() {
    _statusCheckTimer?.cancel();
    _statusCheckTimer = Timer.periodic(Duration(seconds: 30), (timer) async {
      try {
        final service = FlutterBackgroundService();
        final isRunning = await service.isRunning();

        if (isRunning != _isServiceRunning) {
          _isServiceRunning = isRunning;
          print('Статус сервиса изменился: $_isServiceRunning');
        }
      } catch (e) {
        print('Ошибка проверки статуса сервиса: $e');
      }
    });
  }

  /// Проверка текущего статуса сервиса
  Future<bool> checkServiceStatus() async {
    try {
      final service = FlutterBackgroundService();
      _isServiceRunning = await service.isRunning();
      return _isServiceRunning;
    } catch (e) {
      print('Ошибка проверки статуса сервиса: $e');
      return false;
    }
  }

  /// Принудительный запуск сервиса
  Future<bool> startService() async {
    try {
      final service = FlutterBackgroundService();
      service.startService();
      _isServiceRunning = true;
      print('Сервис принудительно запущен');
      return true;
    } catch (e) {
      print('Ошибка принудительного запуска сервиса: $e');
      return false;
    }
  }

  /// Остановка сервиса
  Future<bool> stopService() async {
    try {
      final service = FlutterBackgroundService();
      service.invoke('stopService');
      _isServiceRunning = false;
      print('Сервис остановлен');
      return true;
    } catch (e) {
      print('Ошибка остановки сервиса: $e');
      return false;
    }
  }

  /// Очистка ресурсов
  void dispose() {
    _statusCheckTimer?.cancel();
    _isServiceInitialized = false;
    _isServiceRunning = false;
  }
}
