import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'theme_service.dart';
import 'theme_configurator.dart';
import 'theme_name.dart';

class ThemeCubit extends Cubit<ThemeName> {
  final ThemeService _themeService;
  final ThemeConfigurator _themeConfigurator;

  ThemeCubit(this._themeService, this._themeConfigurator) : super(ThemeName.white);

  Future<void> initialize() async {
    final savedTheme = _themeService.getSavedTheme();
    if (savedTheme != null) {
      emit(savedTheme);
    }
  }

  Future<void> changeTheme(ThemeName theme) async {
    if (state == theme) return;
    emit(theme);
    await _themeService.saveTheme(theme);
  }

  ThemeData getCurrentTheme(BuildContext context) {
    return _themeConfigurator.configureTheme(themeName: state, context: context);
  }
}
