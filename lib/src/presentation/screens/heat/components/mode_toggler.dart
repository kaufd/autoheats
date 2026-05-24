// FILE: lib/src/presentation/screens/heat/components/mode_toggler.dart
// VERSION: 1.1.0
// START_MODULE_CONTRACT
//   PURPOSE: SegmentedButton для выбора HeatMode (manual/presets/auto) per UserType.
//   SCOPE: тап-handler разводит presets-сегмент через onPresetsSegmentTapped,
//          чтобы AppContent мог либо открыть Presets-вкладку, либо применить активный
//          пресет.
//   DEPENDS: M-UI-HEAT, M-MODE, M-ENUMS
//   LINKS: M-UI-HEAT, V-M-UI-HEAT
//   ROLE: RUNTIME
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   ModeToggler - StatelessWidget c ValueChanged-like callback на presets-сегмент
//   _handleSelection - dispatches к ModeCubit.setMode для manual/auto;
//                       вызывает onPresetsSegmentTapped для presets
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.1.0 - Mode-source decoupling: presets-segment routes through AppContent callback]
// END_CHANGE_SUMMARY

import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/cubit/mode_cubit.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

class ModeToggler extends StatelessWidget {
  final UserType user;
  final VoidCallback onPresetsSegmentTapped;

  const ModeToggler({
    super.key,
    required this.user,
    required this.onPresetsSegmentTapped,
  });

  @override
  Widget build(BuildContext context) {
    final ModeCubit cubit = context.watch<ModeCubit>();
    final String selected = cubit.getModeByUser(user);

    void handleSelection(Set<String> newSelection) {
      final value = newSelection.first;
      if (value == HeatMode.presets.name) {
        onPresetsSegmentTapped();
        return;
      }
      cubit.setMode(user, value);
    }

    return Row(
      children: [
        SegmentedButton<String>(
          segments: const [
            ButtonSegment(
              value: 'manual',
              label: Text('Вручную'),
              icon: Icon(Icons.touch_app),
            ),
            ButtonSegment(
              value: 'presets',
              label: Text('Пресеты'),
              icon: Icon(Icons.settings),
            ),
            ButtonSegment(
              value: 'auto',
              label: Text('Авто'),
              icon: Icon(Icons.hdr_auto),
            ),
          ],
          selected: {selected},
          onSelectionChanged: handleSelection,
          showSelectedIcon: false,
        ),
      ],
    );
  }
}
