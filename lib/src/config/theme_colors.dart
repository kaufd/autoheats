import 'package:flutter/material.dart';
import 'color_constants.dart';

class AppThemeColors extends ThemeExtension<AppThemeColors> {
  final Color backgroundDefault;
  final Color backgroundAccent;
  final Color textButtonText;
  final Color textButtonDisabled;
  final Color textBody;
  final Color tabbarDefault;

  AppThemeColors({
    required this.backgroundDefault,
    required this.backgroundAccent,
    required this.textButtonText,
    required this.textButtonDisabled,
    required this.textBody,
    required this.tabbarDefault,
  });

  static AppThemeColors baseThemeColors = AppThemeColors(
    backgroundDefault: ColorConstants.systemBlack,
    backgroundAccent: ColorConstants.accentGreen,
    textButtonText: ColorConstants.systemWhite,
    textButtonDisabled: ColorConstants.systemGrey,
    textBody: ColorConstants.systemBlack,
    tabbarDefault: ColorConstants.systemBlack,
  );

  static AppThemeColors whiteThemeColors = AppThemeColors(
    backgroundDefault: ColorConstants.systemBlack,
    backgroundAccent: ColorConstants.accentWhite,
    textButtonText: ColorConstants.systemBlack,
    textButtonDisabled: ColorConstants.systemGrey,
    textBody: ColorConstants.systemBlack,
    tabbarDefault: ColorConstants.systemBlack,
  );

  static AppThemeColors redThemeColors = AppThemeColors(
    backgroundDefault: ColorConstants.systemBlack,
    backgroundAccent: ColorConstants.accentRed,
    textButtonText: ColorConstants.systemWhite,
    textButtonDisabled: ColorConstants.systemGrey,
    textBody: ColorConstants.systemBlack,
    tabbarDefault: ColorConstants.systemBlack,
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
