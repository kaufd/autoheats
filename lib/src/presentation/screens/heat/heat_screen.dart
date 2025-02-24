import 'package:autoheat/src/extensions/context_extensions.dart';
import 'package:autoheat/src/models/mode.dart';
import 'package:autoheat/src/presentation/screens/heat/components/mode_toggler.dart';
import 'package:flutter/material.dart';

import 'components/seat.dart';

class HeatScreen extends StatelessWidget {
  const HeatScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return IntrinsicWidth(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: (MediaQuery.of(context).size.width - 66) / 2,
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceAround,
              children: [
                Column(
                  children: [
                    ModeToggler(user: UserType.driver),
                  ],
                ),
                SeatBlock(),
              ],
            ),
          ),
          Container(
            height: 400,
            color: context.themeColors.primary.withAlpha(70),
            width: 2,
          ),
          SizedBox(
            width: (MediaQuery.of(context).size.width - 66) / 2,
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceAround,
              children: [
                SeatBlock(),
                Column(
                  children: [
                    ModeToggler(user: UserType.passenger),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
