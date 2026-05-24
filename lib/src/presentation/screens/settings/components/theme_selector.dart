// FILE: lib/src/presentation/screens/settings/components/theme_selector.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Выбор цветовой темы приложения внутри SettingsScreen.
//   SCOPE: base/red/white buttons, ThemeCubit.changeTheme, wrap-safe head-unit layout.
//   DEPENDS: M-UI-SETTINGS, M-THEME
//   LINKS: M-UI-SETTINGS, V-M-UI-SETTINGS, FA-009
//   ROLE: RUNTIME
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   ThemeSelector - compact wrap-safe theme button group
//   _buildThemeButton - fixed-width theme action button
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.2.0 - White theme selected button uses selected foreground token]
//   PREVIOUS_CHANGE: [v1.1.0 - Phase-4 Slice-6: remove self-Expanded and wrap buttons within bounded width]
// END_CHANGE_SUMMARY

import 'package:autoheat/src/presentation/themes/theme_cubit.dart';
import 'package:autoheat/src/presentation/themes/theme_name.dart';
import 'package:autoheat/src/extensions/context_extensions.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

class ThemeSelector extends StatelessWidget {
  const ThemeSelector({super.key});

  @override
  Widget build(BuildContext context) {
    final ThemeCubit themeManager = context.read<ThemeCubit>();
    final String themeName = themeManager.state.key;

    return Align(
      alignment: Alignment.centerRight,
      child: Wrap(
        alignment: WrapAlignment.end,
        crossAxisAlignment: WrapCrossAlignment.center,
        spacing: 24,
        runSpacing: 12,
        children: [
          _buildThemeButton(
            context: context,
            themeManager: themeManager,
            currentThemeKey: themeName,
            themeType: ThemeType.base,
            label: 'Зеленая',
          ),
          _buildThemeButton(
            context: context,
            themeManager: themeManager,
            currentThemeKey: themeName,
            themeType: ThemeType.red,
            label: 'Красная',
          ),
          _buildThemeButton(
            context: context,
            themeManager: themeManager,
            currentThemeKey: themeName,
            themeType: ThemeType.white,
            label: 'Белая',
          ),
        ],
      ),
    );
  }

  Widget _buildThemeButton({
    required BuildContext context,
    required ThemeCubit themeManager,
    required String currentThemeKey,
    required ThemeType themeType,
    required String label,
  }) {
    final isSelected = currentThemeKey == themeType.key;

    return SizedBox(
      width: 180,
      child: TextButton(
        onPressed: () => themeManager.changeTheme(themeType),
        style: ButtonStyle(
          foregroundColor: WidgetStatePropertyAll(
            isSelected ? context.themeColors.textButtonSelected : Colors.white,
          ),
          backgroundColor: WidgetStatePropertyAll(
            isSelected ? context.themeColors.primary : Colors.transparent,
          ),
          side: WidgetStatePropertyAll(
            BorderSide(
              color: isSelected ? context.themeColors.primary : Colors.white,
              width: 0.5,
            ),
          ),
        ),
        child: Text(label),
      ),
    );
  }
}
