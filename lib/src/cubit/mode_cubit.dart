import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/repository/mode/repository.dart';
import 'package:autoheat/src/services/auto_heat_service.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import 'mode_state_cubit.dart';

class ModeCubit extends Cubit<ModesState> {
  final ModeRepository _modeRepository;
  final AutoHeatService _autoHeatService = AutoHeatService();

  ModeCubit(this._modeRepository)
      : super(ModesState(states: [
          ModeState(userType: UserType.driver, heatMode: HeatMode.manual, heatLevel: 0),
          ModeState(userType: UserType.passenger, heatMode: HeatMode.manual, heatLevel: 0),
        ])) {
    _initialize();
  }

  void _initialize() async {
    final modes = await _modeRepository.getAllModes();

    if (modes.isEmpty) {
      _modeRepository.createDefaultModes();
    }

    final states = modes
        .map((mode) => ModeState(
              userType: mode.user,
              heatMode: mode.mode,
              heatLevel: mode.heatLevel,
            ))
        .toList();

    emit(ModesState(states: states));

    // После инициализации проверяем режимы и запускаем соответствующие действия
    _initializeHeatModes(states);
  }

  String getModeByUser(UserType user) {
    return state.states.firstWhere((mode) => mode.userType == user).heatMode.name;
  }

  void setMode(UserType userType, String newMode) async {
    final HeatMode heatMode = HeatModeExtension.fromString(newMode);
    await _modeRepository.setMode(userType, heatMode);

    final updatedStates = state.states.toList();
    final index = updatedStates.indexWhere((state) => state.userType == userType);
    final currentState = updatedStates[index];

    // При переключении в режим "manual" сбрасываем уровень подогрева в 0
    final newHeatLevel = heatMode == HeatMode.manual ? 0 : currentState.heatLevel;

    updatedStates[index] = ModeState(
      userType: userType,
      heatMode: heatMode,
      heatLevel: newHeatLevel,
    );

    // Управляем автоматическим подогревом
    _manageAutoHeat(userType, heatMode);

    emit(ModesState(states: updatedStates));
  }

  int getHeatLevelByUser(UserType user) {
    return state.states.firstWhere((mode) => mode.userType == user).heatLevel;
  }

  void setHeatLevel(UserType userType, int level) async {
    // Сохраняем уровень подогрева в базу данных
    await _modeRepository.setHeatLevel(userType, level);

    final updatedStates = state.states.toList();
    final index = updatedStates.indexWhere((state) => state.userType == userType);
    final currentState = updatedStates[index];
    updatedStates[index] = ModeState(
      userType: userType,
      heatMode: currentState.heatMode,
      heatLevel: level,
    );

    emit(ModesState(states: updatedStates));
  }

  void toggleHeatLevel(UserType userType) {
    final currentMode = state.states.firstWhere((state) => state.userType == userType).heatMode;

    // Переключаем уровень только в ручном режиме
    if (currentMode == HeatMode.manual) {
      final currentLevel = getHeatLevelByUser(userType);
      final newLevel = currentLevel == 3 ? 0 : currentLevel + 1;
      setHeatLevel(userType, newLevel);
    } else {
      // Если не в ручном режиме, переключаем в ручной и устанавливаем уровень 1
      setMode(userType, HeatMode.manual.name);
      setHeatLevel(userType, 1);
    }
  }

  // Управление автоматическим подогревом
  void _manageAutoHeat(UserType userType, HeatMode heatMode) {
    if (heatMode == HeatMode.auto) {
      // Запускаем автоматический подогрев
      _autoHeatService.startAutoHeat(userType, (newLevel) {
        setHeatLevel(userType, newLevel);
      });
    } else {
      // Останавливаем автоматический подогрев
      _autoHeatService.stopAutoHeat(userType);
    }
  }

  // Установить температуру салона (для автоматического режима)
  void setCabinTemperature(double celsius) {
    _autoHeatService.setTemperature(celsius);
  }

  // Получить текущую температуру салона
  double? getCabinTemperature() {
    return _autoHeatService.currentTemperature?.celsius;
  }

  // Инициализировать режимы подогрева при запуске приложения
  void _initializeHeatModes(List<ModeState> states) {
    for (final state in states) {
      if (state.heatMode == HeatMode.auto) {
        // Если режим "Авто" - запускаем автоматический подогрев
        _autoHeatService.startAutoHeat(state.userType, (newLevel) {
          setHeatLevel(state.userType, newLevel);
        });
      } else if (state.heatMode == HeatMode.manual && state.heatLevel > 0) {
        // Если режим "Вручную" и есть сохраненный уровень - восстанавливаем его
        setHeatLevel(state.userType, state.heatLevel);
      } else if (state.heatMode == HeatMode.presets) {
        // Режим "Пресеты" - ничего не делаем, настройки управляются через PresetsScreen
      }
    }
  }

  @override
  Future<void> close() {
    _autoHeatService.dispose();
    return super.close();
  }
}
