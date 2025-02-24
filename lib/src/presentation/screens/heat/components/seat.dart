import 'package:autoheat/src/extensions/context_extensions.dart';
import 'package:flutter/material.dart';

class SeatBlock extends StatefulWidget {
  const SeatBlock({super.key});

  @override
  SeatBlockState createState() => SeatBlockState();
}

class SeatBlockState extends State<SeatBlock> {
  int _level = 0;

  void _onSeatTap() {
    setState(() {
      if (_level == 3) {
        _level = 0;
        return;
      }
      _level = _level + 1;
    });
  }

  @override
  Widget build(BuildContext context) {
    Color getColor(int level, int targetLevel) {
      return level > targetLevel
          ? context.themeColors.backgroundButtonPrimary
          : context.themeColors.backgroundButtonInactive;
    }

    return GestureDetector(
      onTap: _onSeatTap,
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Image.asset(
            'assets/images/seat3.png',
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
                    color: getColor(_level, i),
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
