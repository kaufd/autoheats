import 'package:autoheat/src/models/mode.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:realm/realm.dart';

import 'mode_state_cubit.dart';

class ModeCubit extends Cubit<ModeState> {
  final Realm realm;
  late Mode mode;

  ModeCubit(this.realm) : super(ModeState(heatMode: HeatMode.off, userType: UserType.driver)) {
    mode = realm.all<Mode>().firstOrNull ?? _createDefaultMode();
    emit(ModeState(heatMode: mode.mode, userType: mode.user));
  }

  Mode _createDefaultMode() {
    return realm.write(() {
      return realm.add(Mode(
        UserType.driver.name,
        HeatMode.off.name,
      ));
    });
  }

  void setMode(HeatMode newMode, UserType user) {
    realm.write(() {
      mode.mode = newMode;
    });
    emit(ModeState(heatMode: newMode, userType: user));
  }

  void toggleMode() {
    throw Exception('Need to implement');
  }
}
