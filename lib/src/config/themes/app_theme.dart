import 'package:flutter/material.dart';

import 'theme_colors.dart';

abstract class AppTheme {
  static ThemeData base(BuildContext context) => _buildTheme(
        context,
        colors: AppThemeColors.baseThemeColors,
      );

  static ThemeData red(BuildContext context) => _buildTheme(
        context,
        colors: AppThemeColors.redThemeColors,
      );

  static ThemeData white(BuildContext context) => _buildTheme(
        context,
        colors: AppThemeColors.whiteThemeColors,
      );

  static ThemeData _buildTheme(BuildContext context, {required AppThemeColors colors}) {
    return ThemeData(
      useMaterial3: true,
      extensions: <ThemeExtension<dynamic>>[colors],
      appBarTheme: AppBarTheme(
        backgroundColor: colors.backgroundDefault,
      ),
    );
  }
}
