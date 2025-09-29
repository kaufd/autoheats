import 'package:autoheat/src/config/color_constants.dart';
import 'package:autoheat/src/extensions/context_extensions.dart';
import 'package:flutter/material.dart';

class ErrorBlock extends StatelessWidget {
  final String message;
  final IconData? icon;
  final Color? iconColor;
  final double? iconSize;
  final EdgeInsets? padding;

  const ErrorBlock({
    super.key,
    required this.message,
    this.icon = Icons.error,
    this.iconColor = ColorConstants.error,
    this.iconSize = 32,
    this.padding = const EdgeInsets.all(20.0),
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: padding!,
      child: Center(
        child: Column(
          children: [
            Icon(icon, color: iconColor, size: iconSize),
            const SizedBox(height: 8),
            Text(message, style: context.textStyle.paragraph1),
          ],
        ),
      ),
    );
  }
}
