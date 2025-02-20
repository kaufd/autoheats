import 'package:flutter/material.dart';

import 'theme_colors.dart';

enum AppTheme { green, red, white }

final Map<AppTheme, ThemeData> appThemes = {
  AppTheme.green: ThemeData(
    extensions: <ThemeExtension<dynamic>>[AppThemeColors.baseThemeColors],
    brightness: Brightness.dark,
    useMaterial3: true,
    appBarTheme: AppBarTheme(
      elevation: 0,
      backgroundColor: AppThemeColors.baseThemeColors.backgroundDefault,
      surfaceTintColor: AppThemeColors.baseThemeColors.backgroundDefault,
    ),
  ),
  AppTheme.red: ThemeData(
    extensions: <ThemeExtension<dynamic>>[AppThemeColors.redThemeColors],
    brightness: Brightness.dark,
    useMaterial3: true,
    appBarTheme: AppBarTheme(
      elevation: 0,
      backgroundColor: AppThemeColors.redThemeColors.backgroundDefault,
      surfaceTintColor: AppThemeColors.redThemeColors.backgroundDefault,
    ),
  ),
  AppTheme.white: ThemeData(
    extensions: <ThemeExtension<dynamic>>[AppThemeColors.whiteThemeColors],
    brightness: Brightness.dark,
    useMaterial3: true,
    appBarTheme: AppBarTheme(
      elevation: 0,
      backgroundColor: AppThemeColors.whiteThemeColors.backgroundDefault,
      surfaceTintColor: AppThemeColors.whiteThemeColors.backgroundDefault,
    ),
  ),
};
