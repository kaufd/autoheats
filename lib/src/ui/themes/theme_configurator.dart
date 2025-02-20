import '../../config/app_theme.dart';
import 'theme_name.dart';
import 'package:flutter/material.dart';

class ThemeConfigurator {
  ThemeData configureTheme({
    required ThemeType themeName,
    required BuildContext context,
  }) {
    switch (themeName) {
      case ThemeType.red:
        return AppTheme.red(context);
      case ThemeType.white:
        return AppTheme.white(context);
      case ThemeType.base:
        return AppTheme.base(context);
    }
  }
}
