import 'package:flutter/material.dart';
import 'color_constants.dart';

class AppThemeColors extends ThemeExtension<AppThemeColors> {
  final Color backgroundDefault;
  final Color backgroundButtonPrimary;
  // final Color textButtonPrimaryPressed;
  // final Color textButtonOutline;
  // final Color textButtonOutlinePressed;
  final Color textButtonText;
  final Color textButtonDisabled;
  final Color textBody;

  AppThemeColors({
    required this.backgroundDefault,
    required this.backgroundButtonPrimary,
    // required this.textButtonPrimaryPressed,
    // required this.textButtonOutline,
    // required this.textButtonOutlinePressed,
    required this.textButtonText,
    required this.textButtonDisabled,
    required this.textBody,
  });

  static AppThemeColors baseThemeColors = AppThemeColors(
    backgroundDefault: ColorConstants.accentGreen,
    backgroundButtonPrimary: ColorConstants.accentGreen,
    // textButtonPrimaryPressed: ColorConstants.systemWhite,
    // textButtonOutline: ColorConstants.brandGreen,
    // textButtonOutlinePressed: ColorConstants.brandDarkGreen,
    textButtonText: ColorConstants.systemWhite,
    textButtonDisabled: ColorConstants.systemGrey,
    textBody: ColorConstants.systemBlack,
  );

  static AppThemeColors whiteThemeColors = AppThemeColors(
    backgroundDefault: ColorConstants.accentWhite,
    backgroundButtonPrimary: ColorConstants.accentWhite,
    // textButtonPrimaryPressed: ColorConstants.systemWhite,
    // textButtonOutline: ColorConstants.brandGreen,
    // textButtonOutlinePressed: ColorConstants.brandDarkGreen,
    textButtonText: ColorConstants.systemBlack,
    textButtonDisabled: ColorConstants.systemGrey,
    textBody: ColorConstants.systemBlack,
  );

  static AppThemeColors redThemeColors = AppThemeColors(
    backgroundDefault: ColorConstants.accentRed,
    backgroundButtonPrimary: ColorConstants.accentRed,
    // textButtonPrimaryPressed: ColorConstants.systemWhite,
    // textButtonOutline: ColorConstants.brandGreen,
    // textButtonOutlinePressed: ColorConstants.brandDarkGreen,
    textButtonText: ColorConstants.systemWhite,
    textButtonDisabled: ColorConstants.systemGrey,
    textBody: ColorConstants.systemBlack,
  );

  /// Объединение двух тем (нужно для анимаций)
  @override
  ThemeExtension<AppThemeColors> copyWith() {
    return AppThemeColors.baseThemeColors;
  }

  /// Логика слияния двух тем (необязательно)
  @override
  ThemeExtension<AppThemeColors> lerp(covariant ThemeExtension<AppThemeColors>? other, double t) {
    return AppThemeColors.baseThemeColors;
  }
}
