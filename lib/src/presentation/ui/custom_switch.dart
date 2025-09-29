import 'package:autoheat/src/extensions/context_extensions.dart';
import 'package:flutter/material.dart';

class CustomSwitch extends StatelessWidget {
  final bool value;
  final ValueChanged<bool>? onChanged;
  final double width;
  final double height;
  final double thumbSize;
  final Duration animationDuration;

  const CustomSwitch({
    super.key,
    required this.value,
    this.onChanged,
    this.width = 65,
    this.height = 30,
    this.thumbSize = 30,
    this.animationDuration = const Duration(milliseconds: 200),
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onChanged != null ? () => onChanged!(!value) : null,
      child: Container(
        width: width,
        height: height,
        decoration: BoxDecoration(
          color:
              value ? context.themeColors.primary.withAlpha(100) : context.themeColors.switchThumb,
          borderRadius: BorderRadius.circular(height / 2),
        ),
        child: AnimatedAlign(
          duration: animationDuration,
          alignment: value ? Alignment.centerRight : Alignment.centerLeft,
          child: Container(
            width: thumbSize,
            height: thumbSize,
            decoration: BoxDecoration(
              color: value
                  ? context.themeColors.primary
                  : context.themeColors.backgroundButtonInactive,
              shape: BoxShape.circle,
            ),
          ),
        ),
      ),
    );
  }
}
