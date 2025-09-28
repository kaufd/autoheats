import 'package:autoheat/src/cubit/mode_cubit.dart';
import 'package:autoheat/src/cubit/settings_cubit.dart';
import 'package:autoheat/src/di/service_locator.dart';
import 'package:autoheat/src/presentation/themes/theme_cubit.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

class BlockProviders {
  static initiateBlocs() => [
        BlocProvider<ThemeCubit>(create: (context) => locator<ThemeCubit>()),
        BlocProvider<ModeCubit>(create: (context) => locator<ModeCubit>()),
        BlocProvider<SettingsCubit>(create: (context) => locator<SettingsCubit>()),
      ];
}
