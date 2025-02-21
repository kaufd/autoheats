import 'package:autoheat/src/ui/themes/theme_cubit.dart';
import 'package:autoheat/src/ui/themes/theme_name.dart';
import 'package:autoheat/src/extensions/context_extensions.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final ThemeCubit themeManager = context.read<ThemeCubit>();
    final String themeName = context.read<ThemeCubit>().state.key;

    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(
              'Тема приложения: ',
              style: context.textStyle.textSettings,
            ),
            Expanded(
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
                      child: Text('Зеленая'),
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
                              return context
                                  .themeColors.textButtonPrimary; // Фон для выбранного состояния
                            }
                            return Colors.white; // Фон по умолчанию
                          },
                        ),
                        backgroundColor: WidgetStateProperty.resolveWith<Color>(
                          (Set<WidgetState> states) {
                            if (themeName == ThemeType.red.key) {
                              return context.themeColors.primary; // Фон для выбранного состояния
                            }
                            return Colors.transparent; // Фон по умолчанию
                          },
                        ),
                        side: WidgetStateProperty.resolveWith<BorderSide>(
                          (Set<WidgetState> states) {
                            if (themeName == ThemeType.red.key) {
                              return BorderSide(
                                  color: context.themeColors.primary,
                                  width: 10.5); // Контур для выбранного состояния
                            }
                            return BorderSide(
                                color: Colors.white, width: 0.5); // Контур по умолчанию
                          },
                        ),
                      ),
                      // style: ButtonStyle(
                      //   foregroundColor: WidgetStatePropertyAll(themeName == ThemeType.red.key
                      //       ? context.themeColors.textButtonText
                      //       : Colors.white),
                      //   backgroundColor: WidgetStatePropertyAll(themeName == ThemeType.red.key
                      //       ? context.themeColors.backgroundAccent
                      //       : Colors.transparent),
                      //   side: WidgetStatePropertyAll(themeName == ThemeType.red.key
                      //       ? BorderSide(color: context.themeColors.backgroundAccent, width: 0.5)
                      //       : BorderSide(color: Colors.white, width: 0.5)),
                      // ),
                      child: Text('Красная'),
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
                      child: Text('Белая'),
                    ),
                  ),
                ],
              ),
            )
          ],
        ),
        const SizedBox(height: 80),
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(
              'Показывать температуру в салоне: ',
              style: context.textStyle.textSettings,
            ),
            Switch(
              value: true,
              onChanged: (val) {},
            ),
          ],
        )
      ],
    );
  }
}
