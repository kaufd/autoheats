import 'package:flutter/material.dart';
import 'color_constants.dart';

class AppThemeColors extends ThemeExtension<AppThemeColors> {
  final Color primary;
  final Color backgroundButtonPrimary;
  final Color backgroundButtonInactive;
  final Color switchThumb;
  final Color sliderInactiveTrack;
  final Color textButtonPrimary;
  final Color textButtonSelected;
  final Color textBody;
  final Color textMuted;
  final Color tabbarDefault;

  AppThemeColors({
    required this.primary,
    required this.backgroundButtonPrimary,
    required this.backgroundButtonInactive,
    required this.switchThumb,
    required this.sliderInactiveTrack,
    required this.textButtonPrimary,
    required this.textButtonSelected,
    required this.textBody,
    required this.textMuted,
    required this.tabbarDefault,
  });

  static AppThemeColors baseThemeColors = AppThemeColors(
    primary: ColorConstants.accentGreen,
    backgroundButtonPrimary: ColorConstants.accentGreen,
    backgroundButtonInactive: ColorConstants.systemGrey,
    switchThumb: ColorConstants.systemGreyDark,
    sliderInactiveTrack: ColorConstants.systemWhite.withValues(alpha: 0.6),
    textButtonPrimary: ColorConstants.systemWhite,
    textButtonSelected: ColorConstants.systemWhite,
    textBody: ColorConstants.systemWhite,
    textMuted: ColorConstants.systemWhite.withValues(alpha: 0.7),
    tabbarDefault: ColorConstants.systemBlack,
  );

  static AppThemeColors redThemeColors = AppThemeColors(
    primary: ColorConstants.accentRed,
    backgroundButtonPrimary: ColorConstants.accentRed,
    backgroundButtonInactive: ColorConstants.systemGrey,
    switchThumb: ColorConstants.systemGreyDark,
    sliderInactiveTrack: ColorConstants.systemWhite.withValues(alpha: 0.6),
    textButtonPrimary: ColorConstants.systemWhite,
    textButtonSelected: ColorConstants.systemWhite,
    textBody: ColorConstants.systemWhite,
    textMuted: ColorConstants.systemWhite.withValues(alpha: 0.7),
    tabbarDefault: ColorConstants.systemBlack,
  );

  static AppThemeColors whiteThemeColors = AppThemeColors(
    primary: ColorConstants.accentWhite,
    backgroundButtonPrimary: ColorConstants.accentWhite,
    backgroundButtonInactive: ColorConstants.systemGrey,
    switchThumb: ColorConstants.systemGreyDark,
    sliderInactiveTrack: ColorConstants.systemWhite.withValues(alpha: 0.3),
    textButtonPrimary: ColorConstants.systemWhite,
    textButtonSelected: ColorConstants.systemBlack,
    textBody: ColorConstants.systemWhite,
    textMuted: ColorConstants.systemWhite.withValues(alpha: 0.7),
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
