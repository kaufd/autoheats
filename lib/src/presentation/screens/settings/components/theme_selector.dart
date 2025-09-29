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

    return Expanded(
      child: Row(
        mainAxisAlignment: MainAxisAlignment.end,
        children: [
          SizedBox(
            width: 200,
            child: TextButton(
              onPressed: () => themeManager.changeTheme(ThemeType.base),
              style: ButtonStyle(
                foregroundColor: WidgetStatePropertyAll(themeName == ThemeType.base.key
                    ? context.themeColors.textButtonPrimary
                    : Colors.white),
                backgroundColor: WidgetStatePropertyAll(themeName == ThemeType.base.key
                    ? context.themeColors.primary
                    : Colors.transparent),
                side: WidgetStatePropertyAll(themeName == ThemeType.base.key
                    ? BorderSide(color: context.themeColors.primary, width: 0.5)
                    : BorderSide(color: Colors.white, width: 0.5)),
              ),
              child: const Text('Зеленая'),
            ),
          ),
          const SizedBox(width: 40),
          SizedBox(
            width: 200,
            child: OutlinedButton(
              onPressed: () => themeManager.changeTheme(ThemeType.red),
              style: ButtonStyle(
                foregroundColor: WidgetStateProperty.resolveWith<Color>(
                  (Set<WidgetState> states) {
                    if (themeName == ThemeType.red.key) {
                      return context.themeColors.textButtonPrimary;
                    }
                    return Colors.white;
                  },
                ),
                backgroundColor: WidgetStateProperty.resolveWith<Color>(
                  (Set<WidgetState> states) {
                    if (themeName == ThemeType.red.key) {
                      return context.themeColors.primary;
                    }
                    return Colors.transparent;
                  },
                ),
                side: WidgetStateProperty.resolveWith<BorderSide>(
                  (Set<WidgetState> states) {
                    if (themeName == ThemeType.red.key) {
                      return BorderSide(
                        color: context.themeColors.primary,
                        width: 0.5,
                      );
                    }
                    return BorderSide(color: Colors.white, width: 0.5);
                  },
                ),
              ),
              child: const Text('Красная'),
            ),
          ),
          const SizedBox(width: 40),
          SizedBox(
            width: 200,
            child: TextButton(
              onPressed: () => themeManager.changeTheme(ThemeType.white),
              style: ButtonStyle(
                foregroundColor: WidgetStatePropertyAll(themeName == ThemeType.white.key
                    ? context.themeColors.textButtonPrimary
                    : Colors.white),
                backgroundColor: WidgetStatePropertyAll(themeName == ThemeType.white.key
                    ? context.themeColors.primary
                    : Colors.transparent),
                side: WidgetStatePropertyAll(themeName == ThemeType.white.key
                    ? BorderSide(color: context.themeColors.primary, width: 0.5)
                    : BorderSide(color: Colors.white, width: 0.5)),
              ),
              child: const Text('Белая'),
            ),
          ),
        ],
      ),
    );
  }
}
