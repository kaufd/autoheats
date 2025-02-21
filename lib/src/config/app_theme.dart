import 'package:autoheat/src/config/color_constants.dart';
import 'package:autoheat/src/extensions/context_extensions.dart';
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
      platform: TargetPlatform.android,
      useMaterial3: true,
      brightness: Brightness.dark,
      extensions: <ThemeExtension<dynamic>>[colors],
      appBarTheme: AppBarTheme(
        elevation: 0,
        backgroundColor: Colors.transparent,
      ),
      scaffoldBackgroundColor: ColorConstants.systemBlack,
      colorScheme: ColorScheme.fromSwatch().copyWith(
        primary: colors.primary,
        brightness: Brightness.dark,
      ),
      hoverColor: Colors.transparent,
      splashColor: Colors.transparent,
      fontFamily: 'NotoSans',
      fontFamilyFallback: ['Roboto', 'Helvetica'],
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          foregroundColor: colors.textButtonPrimary,
          backgroundColor: colors.backgroundButtonPrimary,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(30),
          ),
          padding: EdgeInsets.symmetric(horizontal: 40, vertical: 12),
        ),
      ),
      // outlinedButtonTheme: OutlinedButtonThemeData(
      //   style: ButtonStyle(
      //     backgroundColor: WidgetStateProperty.resolveWith<Color>(
      //       (Set<WidgetState> states) {
      //         if (states.contains(WidgetState.selected)) {
      //           return Colors.blue; // Фон для выбранного состояния
      //         }
      //         return Colors.transparent; // Фон по умолчанию
      //       },
      //     ),
      //     side: WidgetStateProperty.resolveWith<BorderSide>(
      //       (Set<WidgetState> states) {
      //         if (states.contains(WidgetState.selected)) {
      //           return BorderSide(color: Colors.red, width: 2); // Контур для выбранного состояния
      //         }
      //         return BorderSide(color: Colors.grey, width: 1); // Контур по умолчанию
      //       },
      //     ),
      //   ),
      // ),
      segmentedButtonTheme: SegmentedButtonThemeData(
        style: ButtonStyle(
          padding: WidgetStatePropertyAll(EdgeInsets.symmetric(horizontal: 20, vertical: 15)),
          side: WidgetStatePropertyAll(BorderSide(style: BorderStyle.none)),
          iconColor: WidgetStateColor.resolveWith((Set<WidgetState> states) {
            if (states.contains(WidgetState.selected)) {
              return colors.textButtonSelected;
            }
            return colors.textButtonPrimary;
          }),
          foregroundColor: WidgetStateColor.resolveWith((Set<WidgetState> states) {
            if (states.contains(WidgetState.selected)) {
              return colors.textButtonSelected;
            }
            return colors.textButtonPrimary;
          }),
          backgroundColor: WidgetStateColor.resolveWith((Set<WidgetState> states) {
            if (states.contains(WidgetState.selected)) {
              return colors.primary;
            }
            return Colors.grey[850] ?? Colors.grey;
          }),
          textStyle: WidgetStatePropertyAll(context.textStyle.textSegmentedButton),
        ),
        // style: SegmentedButton.styleFrom(
        //   side: BorderSide(color: colors.backgroundAccent, width: 1),
        //   backgroundColor: Colors.transparent,
        //   foregroundColor: colors.textButtonDisabled,
        //   selectedBackgroundColor: colors.backgroundAccent,
        //   selectedForegroundColor: colors.textButtonText,
        //   iconColor: colors.textButtonText,
        // ),
      ),
    );
  }
}
