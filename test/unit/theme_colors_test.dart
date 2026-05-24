// FILE: test/unit/theme_colors_test.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Regression тесты цветовых токенов темы.
//   SCOPE: AppThemeColors.lerp и контрастные foreground/background токены.
//   DEPENDS: M-THEME
//   LINKS: V-M-THEME, M-THEME
//   ROLE: TEST
//   MAP_MODE: LOCALS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   scenario-2 - AppThemeColors.lerp не возвращает base/green fallback
// END_MODULE_MAP

import 'package:autoheat/src/config/color_constants.dart';
import 'package:autoheat/src/config/theme_colors.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  // START_BLOCK_THEME_COLOR_LERP
  test('scenario-2: AppThemeColors.lerp reaches target palette', () {
    final lerped = AppThemeColors.redThemeColors.lerp(
      AppThemeColors.whiteThemeColors,
      1,
    );

    expect(lerped.primary, ColorConstants.accentWhite);
    expect(lerped.textButtonSelected, ColorConstants.systemBlack);
  });

  test('scenario-2: AppThemeColors.lerp does not fall back to base green', () {
    final lerped = AppThemeColors.redThemeColors.lerp(
      AppThemeColors.whiteThemeColors,
      0.5,
    );

    expect(lerped.primary, isNot(ColorConstants.accentGreen));
  });
  // END_BLOCK_THEME_COLOR_LERP
}
