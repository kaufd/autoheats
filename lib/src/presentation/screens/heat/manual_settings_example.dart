import 'package:autoheat/src/cubit/manual_settings_cubit.dart';
import 'package:autoheat/src/models/manual_settings.dart';
import 'package:autoheat/src/presentation/screens/heat/manual_settings_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

class ManualSettingsExample extends StatefulWidget {
  const ManualSettingsExample({super.key});

  @override
  State<ManualSettingsExample> createState() => _ManualSettingsExampleState();
}

class _ManualSettingsExampleState extends State<ManualSettingsExample> {
  @override
  void initState() {
    super.initState();
    context.read<ManualSettingsCubit>().initialize();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: BlocBuilder<ManualSettingsCubit, ManualSettingsState>(
        builder: (context, state) {
          if (state.isLoading) {
            return const Center(
              child: CircularProgressIndicator(
                color: Colors.red,
              ),
            );
          }

          if (state.error != null) {
            return Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Icon(
                    Icons.error,
                    color: Colors.red,
                    size: 48,
                  ),
                  const SizedBox(height: 16),
                  Text(
                    'Ошибка загрузки настроек',
                    style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                          color: Colors.white,
                        ),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    state.error!,
                    style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                          color: Colors.white70,
                        ),
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 16),
                  ElevatedButton(
                    onPressed: () {
                      context.read<ManualSettingsCubit>().initialize();
                    },
                    child: const Text('Повторить'),
                  ),
                ],
              ),
            );
          }

          return ManualSettingsScreen(
            settingsState: state,
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
          );
        },
      ),
    );
  }
}
