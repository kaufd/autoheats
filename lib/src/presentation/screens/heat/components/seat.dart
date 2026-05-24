import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/cubit/mode_cubit.dart';
import 'package:autoheat/src/cubit/mode_state_cubit.dart';
import 'package:autoheat/src/extensions/context_extensions.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

class SeatBlock extends StatelessWidget {
  final UserType userType;

  const SeatBlock({super.key, required this.userType});

  void _onSeatTap(BuildContext context) {
    context.read<ModeCubit>().toggleHeatLevel(userType);
  }

  @override
  Widget build(BuildContext context) {
    Color getColor(int level, int targetLevel) {
      return level > targetLevel
          ? context.themeColors.backgroundButtonPrimary
          : context.themeColors.backgroundButtonInactive;
    }

    return BlocBuilder<ModeCubit, ModesState>(
      builder: (context, state) {
        final heatLevel = context.read<ModeCubit>().getHeatLevelByUser(userType);

        return GestureDetector(
          onTap: () => _onSeatTap(context),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Image.asset(
                'assets/images/seat.png',
                height: 300,
              ),
              const SizedBox(height: 20),
              Row(
                children: [
                  ...List.generate(
                    3,
                    (i) => Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 4),
                      child: Icon(
                        Icons.circle_rounded,
                        color: getColor(heatLevel, i),
                      ),
                    ),
                  ),
                ],
              ),
            ],
          ),
        );
      },
    );
  }
}
