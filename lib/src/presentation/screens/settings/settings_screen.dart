import 'package:autoheat/src/cubit/settings_cubit.dart';
import 'package:autoheat/src/cubit/manual_settings_cubit.dart';
import 'package:autoheat/src/models/manual_settings.dart';
import 'package:autoheat/src/presentation/screens/settings/components/presets_screen.dart';
import 'package:autoheat/src/presentation/themes/theme_cubit.dart';
import 'package:autoheat/src/presentation/themes/theme_name.dart';
import 'package:autoheat/src/extensions/context_extensions.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  @override
  void initState() {
    super.initState();
    // Инициализируем настройки ручного режима
    context.read<ManualSettingsCubit>().initialize();
  }

  @override
  Widget build(BuildContext context) {
    final ThemeCubit themeManager = context.read<ThemeCubit>();
    final String themeName = themeManager.state.key;

    return SingleChildScrollView(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.start,
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
                                  width: 10.5,
                                );
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
          const SizedBox(height: 20),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'Показывать температуру в салоне: ',
                style: context.textStyle.textSettings,
              ),
              BlocBuilder<SettingsCubit, SettingsState>(
                builder: (context, settingsState) {
                  return GestureDetector(
                    onTap: () async {
                      await context
                          .read<SettingsCubit>()
                          .setCabinTemperatureVisibility(!settingsState.showCabinTemperature);
                    },
                    child: Container(
                      width: 60,
                      height: 30,
                      decoration: BoxDecoration(
                        color: settingsState.showCabinTemperature
                            ? context.themeColors.primary.withAlpha(100)
                            : context.themeColors.backgroundButtonInactive,
                        borderRadius: BorderRadius.circular(15),
                      ),
                      child: AnimatedAlign(
                        duration: const Duration(milliseconds: 200),
                        alignment: settingsState.showCabinTemperature
                            ? Alignment.centerRight
                            : Alignment.centerLeft,
                        child: Container(
                          width: 26,
                          height: 26,
                          margin: const EdgeInsets.all(2),
                          decoration: BoxDecoration(
                            color: settingsState.showCabinTemperature
                                ? context.themeColors.primary
                                : context.themeColors.backgroundButtonPrimary,
                            shape: BoxShape.circle,
                          ),
                        ),
                      ),
                    ),
                  );
                },
              ),
            ],
          ),
          const SizedBox(height: 20),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'Настройки пресетов:',
                style: context.textStyle.textSettings,
              ),
            ],
          ),
          const SizedBox(height: 10),
          _buildPresetsSection(context),
        ],
      ),
    );
  }

  Widget _buildPresetsSection(BuildContext context) {
    return BlocBuilder<ManualSettingsCubit, ManualSettingsState>(
      builder: (context, settingsState) {
        if (settingsState.isLoading) {
          return const Center(
            child: Padding(
              padding: EdgeInsets.all(20.0),
              child: CircularProgressIndicator(color: Colors.red),
            ),
          );
        }

        if (settingsState.error != null) {
          return Padding(
            padding: const EdgeInsets.all(20.0),
            child: Center(
              child: Column(
                children: [
                  const Icon(Icons.error, color: Colors.red, size: 32),
                  const SizedBox(height: 8),
                  Text(
                    'Ошибка загрузки настроек',
                    style: Theme.of(context).textTheme.bodyMedium?.copyWith(color: Colors.white),
                  ),
                ],
              ),
            ),
          );
        }

        return Container(
          decoration: BoxDecoration(
            color: Colors.black.withValues(alpha: 0.8),
            borderRadius: BorderRadius.circular(12),
            border: Border.all(
              color: context.themeColors.primary.withValues(alpha: 0.3),
              width: 1,
            ),
          ),
          child: PresetsScreen(
            settingsState: settingsState,
            onAutoHeatLevelChanged: (autoHeatLevel, level, userType) {
              context.read<ManualSettingsCubit>().updateAutoHeatLevel(
                    userType,
                    autoHeatLevel,
                    level,
                  );
            },
            onTemperatureThresholdChanged: (temperature, userType) {
              context.read<ManualSettingsCubit>().updateTemperatureThreshold(
                    userType,
                    temperature,
                  );
            },
          ),
        );
      },
    );
  }
}
