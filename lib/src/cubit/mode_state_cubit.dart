import 'package:autoheat/src/models/mode.dart';

class ModeState {
  final UserType userType;
  final HeatMode heatMode;

  ModeState({required this.userType, required this.heatMode});
}

class ModesState {
  final List<ModeState> states;

  ModesState({required this.states});
}
