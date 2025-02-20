import 'package:autoheat/src/ui/themes/theme_cubit.dart';
import 'package:autoheat/src/ui/themes/theme_name.dart';
import 'package:autoheat/src/extensions/context_extensions.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final themeManager = context.read<ThemeCubit>();
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: <Widget>[
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text('Тема приложения: ', style: context.textStyle.heading1),
            Expanded(
              child: Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  ElevatedButton(
                    onPressed: () => themeManager.changeTheme(ThemeType.base),
                    child: Text('Green Theme'),
                  ),
                  const SizedBox(width: 40),
                  ElevatedButton(
                    onPressed: () => themeManager.changeTheme(ThemeType.red),
                    child: Text('Red Theme'),
                  ),
                  const SizedBox(width: 40),
                  ElevatedButton(
                    onPressed: () => themeManager.changeTheme(ThemeType.white),
                    child: Text('White Theme'),
                  ),
                ],
              ),
            )
          ],
        ),
      ],
    );
  }
}
