import 'package:flutter/material.dart';
import 'color_constants.dart';

class AppThemeColors extends ThemeExtension<AppThemeColors> {
  final Color primary;
  final Color backgroundButtonPrimary;
  final Color backgroundButtonInactive;
  final Color textButtonPrimary;
  final Color textButtonSelected;
  final Color textBody;
  final Color tabbarDefault;

  AppThemeColors({
    required this.primary,
    required this.backgroundButtonPrimary,
    required this.backgroundButtonInactive,
    required this.textButtonPrimary,
    required this.textButtonSelected,
    required this.textBody,
    required this.tabbarDefault,
  });

  static AppThemeColors baseThemeColors = AppThemeColors(
    primary: ColorConstants.accentGreen,
    backgroundButtonPrimary: ColorConstants.accentGreen,
    backgroundButtonInactive: ColorConstants.systemGrey,
    textButtonPrimary: ColorConstants.systemWhite,
    textButtonSelected: ColorConstants.systemWhite,
    textBody: ColorConstants.systemWhite,
    tabbarDefault: ColorConstants.systemBlack,
  );

  static AppThemeColors redThemeColors = AppThemeColors(
    primary: ColorConstants.accentRed,
    backgroundButtonPrimary: ColorConstants.accentRed,
    backgroundButtonInactive: ColorConstants.systemGrey,
    textButtonPrimary: ColorConstants.systemWhite,
    textButtonSelected: ColorConstants.systemWhite,
    textBody: ColorConstants.systemWhite,
    tabbarDefault: ColorConstants.systemBlack,
  );

  static AppThemeColors whiteThemeColors = AppThemeColors(
    primary: ColorConstants.accentWhite,
    backgroundButtonPrimary: ColorConstants.accentWhite,
    backgroundButtonInactive: ColorConstants.systemGrey,
    textButtonPrimary: ColorConstants.systemWhite,
    textButtonSelected: ColorConstants.systemBlack,
    textBody: ColorConstants.systemWhite,
    tabbarDefault: ColorConstants.systemWhite,
  );

  @override
  ThemeExtension<AppThemeColors> copyWith() {
    return AppThemeColors.baseThemeColors;
  }

  @override
  ThemeExtension<AppThemeColors> lerp(covariant ThemeExtension<AppThemeColors>? other, double t) {
    return AppThemeColors.baseThemeColors;
  }
}
