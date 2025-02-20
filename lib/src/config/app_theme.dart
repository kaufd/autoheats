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
      brightness: Brightness.dark,
      extensions: <ThemeExtension<dynamic>>[colors],
      appBarTheme: AppBarTheme(
        elevation: 0,
        backgroundColor: Colors.transparent,
      ),
      scaffoldBackgroundColor: colors.backgroundDefault,
      fontFamily: 'SourceSans3',
      fontFamilyFallback: ['Roboto', 'Helvetica'],
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          foregroundColor: colors.textButtonText,
          backgroundColor: colors.backgroundAccent,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(30),
          ),
          padding: EdgeInsets.symmetric(horizontal: 20, vertical: 12),
        ),
      ),
      segmentedButtonTheme: SegmentedButtonThemeData(
        style: ButtonStyle(
          side: WidgetStateProperty.resolveWith((states) {
            return BorderSide(color: Colors.white, width: 0.5);
          }),
          iconColor: WidgetStateColor.resolveWith((states) {
            if (states.contains(WidgetState.selected)) {
              return colors.textButtonText;
            }
            return colors.textButtonDisabled;
          }),
          foregroundColor: WidgetStateColor.resolveWith((states) {
            if (states.contains(WidgetState.selected)) {
              return colors.textButtonText;
            }
            return colors.textButtonDisabled;
          }),
          backgroundColor: WidgetStateColor.resolveWith((states) {
            if (states.contains(WidgetState.selected)) {
              return colors.backgroundAccent;
            }
            return Colors.transparent;
          }),
        ),
      ),
    );
  }
}
