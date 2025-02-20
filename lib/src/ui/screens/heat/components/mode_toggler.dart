import 'package:flutter/material.dart';

class ModeToggler extends StatefulWidget {
  const ModeToggler({super.key});

  @override
  State<ModeToggler> createState() => _ModeTogglerState();
}

class _ModeTogglerState extends State<ModeToggler> {
  String selected = 'auto';

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        SegmentedButton<String>(
          segments: const [
            ButtonSegment(
              value: 'off',
              label: Text('Выкл.'),
              icon: Icon(Icons.not_interested),
            ),
            ButtonSegment(
              value: 'manual',
              label: Text('Вручную'),
              icon: Icon(Icons.touch_app),
            ),
            ButtonSegment(
              value: 'auto',
              label: Text('Авто'),
              icon: Icon(Icons.hdr_auto),
            ),
          ],
          selected: {selected},
          onSelectionChanged: (newSelection) {
            setState(() {
              selected = newSelection.first;
            });
          },
          showSelectedIcon: false,
        ),
      ],
    );
  }
}
