import 'package:autoheat/src/config/text_styles.dart';
import 'package:autoheat/src/config/theme_colors.dart';
import 'package:flutter/material.dart';

extension BuildContextExtension on BuildContext {
  AppThemeColors get themeColors {
    return Theme.of(this).extension<AppThemeColors>() ?? AppThemeColors.baseThemeColors;
  }

  AppTextStyle get textStyle {
    return AppTextStyle(this);
  }

  void ifMounted(Function() callback) {
    if (mounted) {
      callback();
    }
  }
}
