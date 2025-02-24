import 'package:autoheat/src/models/mode.dart';
import 'package:realm/realm.dart';

import 'mode_repository_interface.dart';

class ModeRepository implements ModeRepositoryInterface {
  final Realm _realm;

  ModeRepository(this._realm);

  @override
  Future createDefaultModes() async {
    return _realm.write(() => [
          _realm.add(Mode(
            UserType.driver.name,
            HeatMode.off.name,
          )),
          _realm.add(Mode(
            UserType.passenger.name,
            HeatMode.off.name,
          )),
        ]);
  }

  @override
  Future<List<Mode>> getAllModes() async {
    return _realm.all<Mode>().toList();
  }

  @override
  Future setMode(UserType user, HeatMode mode) async {
    final modeToUpdate = _realm.all<Mode>().firstWhere((mode) => mode.user == user);

    _realm.write(() {
      modeToUpdate.mode = mode;
    });
  }

  // @override
  // Future<String> getModeByUser(String user) async {
  //   final UserType userType = UserTypeExtension.fromString(user);

  //   return _realm.all<Mode>().firstWhere((mode) => mode.user == userType).modeName;
  // }
}
