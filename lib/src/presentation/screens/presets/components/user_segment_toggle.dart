import 'package:autoheat/src/app_enums.dart';
import 'package:flutter/material.dart';

class UserSegmentToggle extends StatelessWidget {
  final UserType selectedUser;
  final ValueChanged<UserType> onChanged;

  const UserSegmentToggle({
    super.key,
    required this.selectedUser,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    return SegmentedButton<UserType>(
      segments: const [
        ButtonSegment(value: UserType.driver, label: Text('Водитель')),
        ButtonSegment(value: UserType.passenger, label: Text('Пассажир')),
      ],
      selected: {selectedUser},
      showSelectedIcon: false,
      onSelectionChanged: (set) => onChanged(set.first),
    );
  }
}
