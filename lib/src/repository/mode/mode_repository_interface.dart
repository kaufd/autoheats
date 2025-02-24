import 'package:autoheat/src/models/mode.dart';

abstract interface class ModeRepositoryInterface {
  Future<List<Mode>> getAllModes();
  // Future<String?> getModeByUser(String user);
  Future<void> setMode(UserType user, HeatMode mode);
  Future<void> createDefaultModes();
}
