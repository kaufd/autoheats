import 'app_theme.dart';
import 'theme_name.dart';
import 'package:flutter/material.dart';

class ThemeConfigurator {
  ThemeData configureTheme({
    required ThemeName themeName,
    required BuildContext context,
  }) {
    switch (themeName) {
      case ThemeName.red:
        return AppTheme.red(context);
      case ThemeName.white:
        return AppTheme.white(context);
      case ThemeName.base:
        return AppTheme.base(context);
    }
  }
}
