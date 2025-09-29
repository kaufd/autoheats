import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/cubit/mode_cubit.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

class ModeToggler extends StatelessWidget {
  final UserType user;

  const ModeToggler({super.key, required this.user});

  @override
  Widget build(BuildContext context) {
    final ModeCubit state = context.watch<ModeCubit>();
    final String selected = state.getModeByUser(user);

    changeMode(Set<String> newSelection) {
      state.setMode(user, newSelection.first);
    }

    return Row(
      children: [
        SegmentedButton<String>(
          segments: [
            ButtonSegment(
              value: HeatMode.manual.name,
              label: Text('Вручную'),
              icon: Icon(Icons.touch_app),
            ),
            ButtonSegment(
              value: HeatMode.presets.name,
              label: Text('Пресеты'),
              icon: Icon(Icons.settings),
            ),
            ButtonSegment(
              value: HeatMode.auto.name,
              label: Text('Авто'),
              icon: Icon(Icons.hdr_auto),
            ),
          ],
          selected: {selected},
          onSelectionChanged: changeMode,
          showSelectedIcon: false,
        ),
      ],
    );
  }
}
