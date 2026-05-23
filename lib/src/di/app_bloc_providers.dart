// FILE: lib/src/di/app_bloc_providers.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Список BlocProvider'ов для корневого MultiBlocProvider.
//   SCOPE: BlockProviders.initiateBlocs(), provider order, locator-backed singleton cubits.
//   DEPENDS: M-DI, M-THEME, M-MODE, M-CABIN-TEMPERATURE, M-SETTINGS, M-MANUAL-SETTINGS, M-PRESET
//   LINKS: M-BLOC-PROVIDERS, V-M-BLOC-PROVIDERS, DF-INIT-TEMP
//   ROLE: RUNTIME
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   BlockProviders - static factory for root BlocProvider list
//   initiateBlocs - Theme, Mode, CabinTemperature, Settings, ManualSettings, Preset providers
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.0.0 - Phase-4 Slice-3: добавлен CabinTemperatureCubit provider]
// END_CHANGE_SUMMARY

import 'package:autoheat/src/cubit/cabin_temperature_cubit.dart';
import 'package:autoheat/src/cubit/mode_cubit.dart';
import 'package:autoheat/src/cubit/settings_cubit.dart';
import 'package:autoheat/src/cubit/manual_settings_cubit.dart';
import 'package:autoheat/src/cubit/preset_cubit.dart';
import 'package:autoheat/src/di/service_locator.dart';
import 'package:autoheat/src/presentation/themes/theme_cubit.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

class BlockProviders {
  static initiateBlocs() => [
        BlocProvider<ThemeCubit>(create: (context) => locator<ThemeCubit>()),
        BlocProvider<ModeCubit>(create: (context) => locator<ModeCubit>()),
        BlocProvider<CabinTemperatureCubit>(
          create: (context) => locator<CabinTemperatureCubit>(),
        ),
        BlocProvider<SettingsCubit>(
            create: (context) => locator<SettingsCubit>()),
        BlocProvider<ManualSettingsCubit>(
            create: (context) => locator<ManualSettingsCubit>()),
        BlocProvider<PresetCubit>(create: (context) => locator<PresetCubit>()),
      ];
}
