import 'package:autoheat/src/extensions/context_extensions.dart';
import 'package:autoheat/src/models/preset.dart';
import 'package:autoheat/src/cubit/preset_cubit.dart';
import 'package:autoheat/src/presentation/screens/presets/components/preset_list.dart';
import 'package:autoheat/src/presentation/ui/error_block.dart';
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
            style: context.textStyle.paragraph3
                .copyWith(color: context.themeColors.textMuted),
          ),
          const SizedBox(height: 20),
          Expanded(
            child: BlocBuilder<PresetCubit, PresetState>(
              builder: (context, state) {
                if (state.isLoading) {
                  return Center(
                    child: CircularProgressIndicator(
                      color: context.themeColors.primary,
                    ),
                  );
                }

                if (state.error != null) {
                  return const ErrorBlock(
                    message: 'Ошибка загрузки пресетов',
                  );
                }

                return PresetList(
                  presets: state.presets,
                  selectedPresets: state.selectedPresets,
                  onPresetSelected: (preset) {
                    widget.onPresetApplied(preset);
                  },
                  onPresetDeleted: (preset) {
                    context
                        .read<PresetCubit>()
                        .deletePreset(preset.id, preset.userType);
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
