import 'package:autoheat/src/app_enums.dart';

class ModeState {
  final UserType userType;
  final HeatMode heatMode;
  final int heatLevel;

  ModeState({required this.userType, required this.heatMode, this.heatLevel = 0});
}

class ModesState {
  final List<ModeState> states;

  ModesState({required this.states});
}
