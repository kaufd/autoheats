import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'theme_service.dart';
import 'theme_configurator.dart';
import 'theme_name.dart';

class ThemeCubit extends Cubit<ThemeType> {
  final ThemeService _themeService;
  final ThemeConfigurator _themeConfigurator;

  ThemeCubit(this._themeService, this._themeConfigurator) : super(ThemeType.base);

  Future<void> initialize() async {
    final savedTheme = _themeService.getSavedTheme();
    if (savedTheme != null) {
      emit(savedTheme);
    }
  }

  Future<void> changeTheme(ThemeType theme) async {
    if (state == theme) return;
    emit(theme);
    await _themeService.saveTheme(theme);
  }

  ThemeData getCurrentTheme(BuildContext context) {
    return _themeConfigurator.configureTheme(themeName: state, context: context);
  }

  String? getCurrentThemeName() {
    return _themeService.currentTheme;
  }
}
