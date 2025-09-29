import 'package:autoheat/src/app_enums.dart';
import 'package:android_automotive_plugin/car/hvac_manager.dart';

class SeatHeatService {
  final CarHvacManager _carHvacManager;

  SeatHeatService(this._carHvacManager);

  Future<void> setSeatHeatLevel(UserType userType, int level) async {
    try {
      final isDriver = userType == UserType.driver;
      await _carHvacManager.setSeatHeatLevel(isDriver, level);
      print('SeatHeatService: Установлен $level для ${isDriver ? "водителя" : "пассажира"}');
    } catch (e) {
      print('SeatHeatService: Ошибка при установке уровня подогрева: $e');
    }
  }
}
