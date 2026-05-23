// FILE: lib/src/cubit/cabin_temperature_cubit.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: UI-state проекция температуры салона для CabinTemperatureDisplay.
//   SCOPE: подписка на HvacService cabin-temperature multi-listener,
//          initial read, cached emitCurrent, cleanup on close.
//   DEPENDS: M-HVAC, M-LOGGER
//   LINKS: M-CABIN-TEMPERATURE, V-M-CABIN-TEMPERATURE, DF-INIT-TEMP, FA-003
//   ROLE: RUNTIME
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   CabinTemperatureState - Equatable state { celsius?, isLoading }
//   CabinTemperatureCubit - Cubit<CabinTemperatureState> over HvacService listeners
//   _initialize - initial getCabinTemperature() when HvacService cache is empty
//   _onTemperatureChanged - listener target, emits non-loading temperature state
//   close - removeCabinTemperatureListener + Cubit close
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.0.0 - Phase-4 Slice-3: выделен CabinTemperatureCubit]
// END_CHANGE_SUMMARY

import 'package:autoheat/src/services/hvac_service.dart';
import 'package:equatable/equatable.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

class CabinTemperatureState extends Equatable {
  final double? celsius;
  final bool isLoading;

  const CabinTemperatureState({
    this.celsius,
    this.isLoading = true,
  });

  @override
  List<Object?> get props => [celsius, isLoading];
}

class CabinTemperatureCubit extends Cubit<CabinTemperatureState> {
  final HvacService _hvacService;
  late final CabinTemperatureListener _temperatureListener;

  CabinTemperatureCubit(this._hvacService)
      : super(const CabinTemperatureState()) {
    _temperatureListener = _onTemperatureChanged;
    _hvacService.addCabinTemperatureListener(
      _temperatureListener,
      emitCurrent: true,
    );

    if (_hvacService.lastCabinTemperature == null) {
      _initialize();
    }
  }

  Future<void> _initialize() async {
    await _hvacService.getCabinTemperature();
    if (!isClosed && state.isLoading) {
      emit(CabinTemperatureState(
        celsius: _hvacService.lastCabinTemperature,
        isLoading: false,
      ));
    }
  }

  void _onTemperatureChanged(double celsius) {
    if (isClosed) return;
    emit(CabinTemperatureState(celsius: celsius, isLoading: false));
  }

  @override
  Future<void> close() {
    _hvacService.removeCabinTemperatureListener(_temperatureListener);
    return super.close();
  }
}
