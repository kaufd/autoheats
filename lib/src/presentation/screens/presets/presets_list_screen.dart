import 'package:autoheat/src/models/preset.dart';
import 'package:autoheat/src/cubit/preset_cubit.dart';
import 'package:autoheat/src/presentation/screens/presets/components/preset_list.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

class PresetsListScreen extends StatefulWidget {
  final Function(Preset) onPresetApplied;

  const PresetsListScreen({
    super.key,
    required this.onPresetApplied,
  });

  @override
  State<PresetsListScreen> createState() => _PresetsListScreenState();
}

class _PresetsListScreenState extends State<PresetsListScreen> {
  @override
  void initState() {
    super.initState();
    context.read<PresetCubit>().loadAllPresets();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.transparent,
      body: Column(
        children: [
          Text(
            'Пресеты сохраняются отдельно для водителя и пассажира. Для сохранения пресета используйте кнопки в разделе "Настройки".',
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: Colors.white.withValues(alpha: 0.7),
                ),
          ),
          const SizedBox(height: 20),
          Expanded(
            child: BlocBuilder<PresetCubit, PresetState>(
              builder: (context, state) {
                if (state.isLoading) {
                  return const Center(
                    child: CircularProgressIndicator(
                      color: Colors.white,
                    ),
                  );
                }

                if (state.error != null) {
                  return Center(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(
                          Icons.error,
                          color: Colors.red,
                          size: 48,
                        ),
                        const SizedBox(height: 16),
                        Text(
                          'Ошибка загрузки пресетов',
                          style: Theme.of(context).textTheme.titleMedium?.copyWith(
                                color: Colors.white,
                              ),
                        ),
                        const SizedBox(height: 8),
                      ],
                    ),
                  );
                }

                return PresetList(
                  presets: state.presets,
                  selectedPreset: state.selectedPreset,
                  onPresetSelected: (preset) {
                    widget.onPresetApplied(preset);
                  },
                  onPresetDeleted: (preset) {
                    context.read<PresetCubit>().deletePreset(preset.id, preset.userType);
                  },
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}
