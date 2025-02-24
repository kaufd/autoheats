import 'package:autoheat/src/models/mode.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:realm/realm.dart';

import 'mode_state_cubit.dart';

class ModeCubit extends Cubit<ModesState> {
  final Realm realm;
  late List<Mode> modeList;

  ModeCubit(this.realm)
      : super(ModesState(states: [
          ModeState(userType: UserType.driver, heatMode: HeatMode.off),
          ModeState(userType: UserType.passenger, heatMode: HeatMode.off),
        ])) {
    modeList = realm.all<Mode>().toList();

    if (modeList.isEmpty) {
      _createDefaultModes();
    }

    emit(ModesState(
        states: modeList
            .map((mode) => ModeState(
                  userType: mode.user,
                  heatMode: mode.mode,
                ))
            .toList()));
  }

  void _createDefaultModes() {
    realm.write(() {
      modeList = [
        realm.add(Mode(
          UserType.driver.name,
          HeatMode.off.name,
        )),
        realm.add(Mode(
          UserType.passenger.name,
          HeatMode.off.name,
        )),
      ];
    });
  }

  void setMode(UserType userType, HeatMode newMode) {
    final modeToUpdate = modeList.firstWhere(
      (mode) => mode.user == userType,
      orElse: () => throw Exception('Mode with userType $userType not found'),
    );

    realm.write(() {
      modeToUpdate.mode = newMode;
    });

    final updatedStates = state.states.toList();
    final index = modeList.indexOf(modeToUpdate);
    updatedStates[index] = ModeState(userType: userType, heatMode: newMode);

    emit(ModesState(states: updatedStates));
  }

  // void setMode(int index, HeatMode newMode, UserType userType) {
  //   realm.write(() {
  //     modeList[index] = Mode(userType.name, newMode.name);
  //   });

  //   final updatedStates = state.states.toList();
  //   updatedStates[index] = ModeState(userType: userType, heatMode: newMode);

  //   emit(ModesState(states: updatedStates));
  // }
}
