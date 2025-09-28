import 'package:autoheat/src/services/settings_service.dart';
import 'package:equatable/equatable.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

class SettingsState extends Equatable {
  final bool showCabinTemperature;

  const SettingsState({
    this.showCabinTemperature = true,
  });

  SettingsState copyWith({
    bool? showCabinTemperature,
  }) {
    return SettingsState(
      showCabinTemperature: showCabinTemperature ?? this.showCabinTemperature,
    );
  }

  @override
  List<Object?> get props => [showCabinTemperature];
}

class SettingsCubit extends Cubit<SettingsState> {
  final SettingsService _settingsService;

  SettingsCubit(this._settingsService) : super(const SettingsState());

  Future<void> initialize() async {
    final showCabinTemperature = _settingsService.getShowCabinTemperature();
    emit(state.copyWith(showCabinTemperature: showCabinTemperature));
  }

  Future<void> toggleCabinTemperatureVisibility() async {
    final newValue = !state.showCabinTemperature;
    emit(state.copyWith(showCabinTemperature: newValue));
    await _settingsService.setShowCabinTemperature(newValue);
  }

  Future<void> setCabinTemperatureVisibility(bool show) async {
    if (state.showCabinTemperature == show) return;
    emit(state.copyWith(showCabinTemperature: show));
    await _settingsService.setShowCabinTemperature(show);
  }
}
