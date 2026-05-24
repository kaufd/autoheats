// FILE: lib/src/config/theme_colors.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: ThemeExtension color tokens for base/red/white app themes.
//   SCOPE: AppThemeColors token sets, copyWith, field-wise lerp for theme transitions.
//   DEPENDS: none
//   LINKS: M-THEME, V-M-THEME
//   ROLE: TYPES
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   AppThemeColors - ThemeExtension token container for app UI colors
//   baseThemeColors/redThemeColors/whiteThemeColors - concrete theme palettes
//   copyWith - token override copy
//   lerp - field-wise Color.lerp without base/green fallback
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.1.0 - White theme contrast and field-wise theme color lerp]
// END_CHANGE_SUMMARY

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

  const AppThemeColors({
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
  AppThemeColors copyWith({
    Color? primary,
    Color? backgroundButtonPrimary,
    Color? backgroundButtonInactive,
    Color? switchThumb,
    Color? sliderInactiveTrack,
    Color? textButtonPrimary,
    Color? textButtonSelected,
    Color? textBody,
    Color? textMuted,
    Color? tabbarDefault,
  }) {
    return AppThemeColors(
      primary: primary ?? this.primary,
      backgroundButtonPrimary:
          backgroundButtonPrimary ?? this.backgroundButtonPrimary,
      backgroundButtonInactive:
          backgroundButtonInactive ?? this.backgroundButtonInactive,
      switchThumb: switchThumb ?? this.switchThumb,
      sliderInactiveTrack: sliderInactiveTrack ?? this.sliderInactiveTrack,
      textButtonPrimary: textButtonPrimary ?? this.textButtonPrimary,
      textButtonSelected: textButtonSelected ?? this.textButtonSelected,
      textBody: textBody ?? this.textBody,
      textMuted: textMuted ?? this.textMuted,
      tabbarDefault: tabbarDefault ?? this.tabbarDefault,
    );
  }

  @override
  AppThemeColors lerp(
    covariant ThemeExtension<AppThemeColors>? other,
    double t,
  ) {
    if (other is! AppThemeColors) return this;

    return AppThemeColors(
      primary: Color.lerp(primary, other.primary, t)!,
      backgroundButtonPrimary: Color.lerp(
        backgroundButtonPrimary,
        other.backgroundButtonPrimary,
        t,
      )!,
      backgroundButtonInactive: Color.lerp(
        backgroundButtonInactive,
        other.backgroundButtonInactive,
        t,
      )!,
      switchThumb: Color.lerp(switchThumb, other.switchThumb, t)!,
      sliderInactiveTrack: Color.lerp(
        sliderInactiveTrack,
        other.sliderInactiveTrack,
        t,
      )!,
      textButtonPrimary:
          Color.lerp(textButtonPrimary, other.textButtonPrimary, t)!,
      textButtonSelected:
          Color.lerp(textButtonSelected, other.textButtonSelected, t)!,
      textBody: Color.lerp(textBody, other.textBody, t)!,
      textMuted: Color.lerp(textMuted, other.textMuted, t)!,
      tabbarDefault: Color.lerp(tabbarDefault, other.tabbarDefault, t)!,
    );
  }
}
