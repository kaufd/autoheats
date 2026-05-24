// FILE: lib/src/presentation/screens/heat/heat_screen.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Главный экран управления подогревом driver/passenger.
//   SCOPE: cabin temperature display, mode togglers, manual level selectors, seat blocks.
//   DEPENDS: M-UI-HEAT, M-MODE, M-CABIN-TEMPERATURE, M-THEME
//   LINKS: M-UI-HEAT, V-M-UI-HEAT, DF-SET-HEAT
//   ROLE: RUNTIME
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   HeatScreen - two-column driver/passenger heat controls
//   build - lays out CabinTemperatureDisplay, ModeToggler, ManualHeatLevelSelector, SeatBlock
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.4.0 - Mode-source decoupling: pass onPresetsSegmentTapped callback to ModeToggler]
//   PREVIOUS_CHANGE: [v1.3.0 - Restore plain Column; selector reserves layout space via Visibility(maintainSize) so ModeToggler stays at constant Y across modes]
// END_CHANGE_SUMMARY

import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/extensions/context_extensions.dart';
import 'package:autoheat/src/presentation/screens/heat/components/manual_heat_level_selector.dart';
import 'package:autoheat/src/presentation/screens/heat/components/mode_toggler.dart';
import 'package:flutter/material.dart';

import 'components/seat.dart';
import 'components/cabin_temperature_display.dart';

class HeatScreen extends StatelessWidget {
  final void Function(UserType user) onPresetsSegmentTapped;

  const HeatScreen({super.key, required this.onPresetsSegmentTapped});

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        const CabinTemperatureDisplay(),
        IntrinsicWidth(
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              SizedBox(
                width: (MediaQuery.of(context).size.width - 66) / 2,
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceAround,
                  crossAxisAlignment: CrossAxisAlignment.center,
                  children: [
                    Column(
                      children: [
                        ModeToggler(
                          user: UserType.driver,
                          onPresetsSegmentTapped: () =>
                              onPresetsSegmentTapped(UserType.driver),
                        ),
                        const SizedBox(height: 60),
                        ManualHeatLevelSelector(userType: UserType.driver),
                      ],
                    ),
                    SeatBlock(userType: UserType.driver),
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
                  crossAxisAlignment: CrossAxisAlignment.center,
                  children: [
                    SeatBlock(userType: UserType.passenger),
                    Column(
                      children: [
                        ModeToggler(
                          user: UserType.passenger,
                          onPresetsSegmentTapped: () =>
                              onPresetsSegmentTapped(UserType.passenger),
                        ),
                        const SizedBox(height: 60),
                        ManualHeatLevelSelector(userType: UserType.passenger),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}
