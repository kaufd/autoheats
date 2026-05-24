// FILE: lib/src/presentation/screens/heat/components/manual_heat_level_selector.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Compact segmented control для прямого выбора manual heat level.
//   SCOPE: levels 1/2/3/off, visibility только в HeatMode.manual (но layout space всегда зарезервирован), ModeCubit.setHeatLevel.
//   DEPENDS: M-UI-HEAT, M-MODE, M-ENUMS, M-THEME
//   LINKS: M-UI-HEAT, M-MODE, DF-SET-HEAT
//   ROLE: RUNTIME
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   ManualHeatLevelSelector - centered compact SegmentedButton<int>, always laid out, visible only in manual mode
//   build - reads ModeCubit state and toggles Visibility while reserving size so parent layout stays stable
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.1.0 - Use Visibility(maintainSize) so non-manual modes keep reserved space and parent Column height stays constant]
//   PREVIOUS_CHANGE: [v1.0.0 - Add compact manual heat level selector]
// END_CHANGE_SUMMARY

import 'dart:async';

import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/cubit/mode_cubit.dart';
import 'package:autoheat/src/cubit/mode_state_cubit.dart';
import 'package:autoheat/src/extensions/context_extensions.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

class ManualHeatLevelSelector extends StatelessWidget {
  final UserType userType;

  const ManualHeatLevelSelector({
    super.key,
    required this.userType,
  });

  @override
  Widget build(BuildContext context) {
    return BlocBuilder<ModeCubit, ModesState>(
      builder: (context, state) {
        final modeCubit = context.read<ModeCubit>();
        final selectedMode = HeatModeExtension.fromString(
          modeCubit.getModeByUser(userType),
        );

        final isManual = selectedMode == HeatMode.manual;
        final selectedLevel = modeCubit.getHeatLevelByUser(userType);

        return Visibility(
          visible: isManual,
          maintainSize: true,
          maintainState: true,
          maintainAnimation: true,
          child: Align(
            alignment: Alignment.center,
            child: SegmentedButton<int>(
              key: ValueKey('manual-heat-selector-${userType.name}'),
              segments: const [
                ButtonSegment(value: 1, label: Text('1')),
                ButtonSegment(value: 2, label: Text('2')),
                ButtonSegment(value: 3, label: Text('3')),
                ButtonSegment(value: 0, label: Text('OFF')),
              ],
              selected: {selectedLevel.clamp(0, 3)},
              showSelectedIcon: false,
              style: ButtonStyle(
                padding: const WidgetStatePropertyAll(
                  EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                ),
                minimumSize: const WidgetStatePropertyAll(Size(42, 34)),
                tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                visualDensity: VisualDensity.compact,
                textStyle: WidgetStatePropertyAll(
                  context.textStyle.textSegmentedButton.copyWith(
                    fontSize: 14,
                    fontWeight: FontWeight.w500,
                  ),
                ),
              ),
              onSelectionChanged: (newSelection) {
                unawaited(modeCubit.setHeatLevel(userType, newSelection.first));
              },
            ),
          ),
        );
      },
    );
  }
}
