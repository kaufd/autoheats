import 'package:autoheat/src/extensions/context_extensions.dart';
import 'package:autoheat/src/ui/screens/heat/components/mode_toggler.dart';
import 'package:flutter/material.dart';

class HeatScreen extends StatelessWidget {
  const HeatScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Padding(
        padding: const EdgeInsets.fromLTRB(32, 46, 16, 32),
        child: IntrinsicWidth(
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              SizedBox(
                width: (MediaQuery.of(context).size.width - 64) / 2,
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceAround,
                  children: [
                    ModeToggler(),
                    Image.asset(
                      'assets/images/seat.png',
                      height: 380,
                    ),
                  ],
                ),
              ),
              Container(
                height: 400,
                color: context.themeColors.backgroundAccent.withAlpha(70),
                width: 2,
              ),
              SizedBox(
                width: (MediaQuery.of(context).size.width - 64) / 2,
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceAround,
                  children: [
                    Image.asset(
                      "assets/images/seat.png",
                      height: 380,
                    ),
                    ModeToggler(),
                  ],
                ),
              ),
            ],
          ),
        ));
  }
}
