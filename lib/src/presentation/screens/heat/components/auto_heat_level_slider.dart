import 'package:autoheat/src/models/manual_settings.dart';
import 'package:autoheat/src/extensions/context_extensions.dart';
import 'package:flutter/material.dart';

class AutoHeatLevelSlider extends StatelessWidget {
  final AutoHeatLevel autoHeatLevel;
  final ValueChanged<int> onLevelChanged;
  final String durationLabel;
  final int levelIndex;

  const AutoHeatLevelSlider({
    super.key,
    required this.autoHeatLevel,
    required this.onLevelChanged,
    required this.durationLabel,
    required this.levelIndex,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const SizedBox(height: 6),
        _buildSlider(context),
        const SizedBox(height: 8),
      ],
    );
  }

  Widget _buildSlider(BuildContext context) {
    return SizedBox(
      height: 32,
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
            decoration: BoxDecoration(
              color: context.themeColors.primary.withValues(alpha: 0.2),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(
                color: context.themeColors.primary,
                width: 1,
              ),
            ),
            child: Text(
              (levelIndex + 1).toString(),
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                    color: context.themeColors.primary,
                    fontWeight: FontWeight.w600,
                    fontSize: 10,
                  ),
            ),
          ),
          const SizedBox(width: 6),
          Expanded(
            child: SliderTheme(
              data: SliderTheme.of(context).copyWith(
                activeTrackColor: context.themeColors.primary,
                inactiveTrackColor: Colors.white.withValues(alpha: 0.3),
                thumbColor: context.themeColors.primary,
                thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 10),
                trackHeight: 8,
                overlayShape: const RoundSliderOverlayShape(overlayRadius: 16),
              ),
              child: Slider(
                value: autoHeatLevel.duration.clamp(0, 15).toDouble(),
                min: 0,
                max: 15,
                divisions: 15,
                onChanged: (value) {
                  onLevelChanged(value.round());
                },
              ),
            ),
          ),
          const SizedBox(width: 6),
          _buildLevelIndicator(context),
        ],
      ),
    );
  }

  Widget _buildLevelIndicator(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      decoration: BoxDecoration(
        color: context.themeColors.primary.withValues(alpha: 0.2),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: context.themeColors.primary.withValues(alpha: 0.5),
          width: 1,
        ),
      ),
      child: Text(
        '${autoHeatLevel.duration} мин.',
        style: Theme.of(context).textTheme.bodySmall?.copyWith(
              color: context.themeColors.primary,
              fontWeight: FontWeight.w600,
            ),
      ),
    );
  }
}
