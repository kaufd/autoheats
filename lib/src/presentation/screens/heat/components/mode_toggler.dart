import 'package:autoheat/src/cubit/mode_cubit.dart';
import 'package:autoheat/src/extensions/context_extensions.dart';
import 'package:autoheat/src/models/mode.dart';
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

    return Column(
      children: [
        const SizedBox(height: 20),
        Row(
          children: [
            SegmentedButton<String>(
              segments: [
                ButtonSegment(
                  value: HeatMode.off.name,
                  label: Text('Выкл.'),
                  icon: Icon(Icons.not_interested),
                ),
                ButtonSegment(
                  value: HeatMode.manual.name,
                  label: Text('Вручную'),
                  icon: Icon(Icons.touch_app),
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
        ),
        Padding(
          padding: const EdgeInsets.only(top: 20.0),
          child: Text(
            'Current mode for ${user.name} is $selected',
            style: context.textStyle.paragraph1.copyWith(color: Colors.white),
          ),
        ),
      ],
    );
  }
}
