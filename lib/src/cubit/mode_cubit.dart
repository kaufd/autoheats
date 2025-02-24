import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/repository/mode/repository.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import 'mode_state_cubit.dart';

class ModeCubit extends Cubit<ModesState> {
  final ModeRepository _modeRepository;

  ModeCubit(this._modeRepository)
      : super(ModesState(states: [
          ModeState(userType: UserType.driver, heatMode: HeatMode.off),
          ModeState(userType: UserType.passenger, heatMode: HeatMode.off),
        ])) {
    _initialize();
  }

  void _initialize() async {
    final modes = await _modeRepository.getAllModes();

    if (modes.isEmpty) {
      _modeRepository.createDefaultModes();
    }

    emit(ModesState(
        states: modes
            .map((mode) => ModeState(
                  userType: mode.user,
                  heatMode: mode.mode,
                ))
            .toList()));
  }

  String getModeByUser(UserType user) {
    return state.states.firstWhere((mode) => mode.userType == user).heatMode.name;
  }

  void setMode(UserType userType, String newMode) async {
    final HeatMode heatMode = HeatModeExtension.fromString(newMode);
    await _modeRepository.setMode(userType, heatMode);

    final updatedStates = state.states.toList();
    final index = updatedStates.indexWhere((state) => state.userType == userType);
    updatedStates[index] = ModeState(userType: userType, heatMode: heatMode);

    emit(ModesState(states: updatedStates));
  }
}
