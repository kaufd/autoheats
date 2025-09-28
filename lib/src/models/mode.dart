import 'package:autoheat/src/app_enums.dart';
import 'package:realm/realm.dart';

part 'mode.realm.dart';

@RealmModel()
class _Mode {
  late String userName;
  late String modeName;
  late int heatLevel;

  UserType get user => UserType.values.firstWhere((e) => e.name == userName);

  HeatMode get mode => HeatMode.values.firstWhere((e) => e.name == modeName);

  set user(UserType value) {
    userName = value.name;
  }

  set mode(HeatMode value) {
    modeName = value.name;
  }
}
