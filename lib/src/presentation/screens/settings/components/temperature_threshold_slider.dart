import 'package:autoheat/src/extensions/context_extensions.dart';
import 'package:autoheat/src/constants/temperature_constants.dart';
import 'package:flutter/material.dart';

class TemperatureThresholdSlider extends StatelessWidget {
  final double temperatureThreshold;
  final ValueChanged<double> onTemperatureChanged;

  const TemperatureThresholdSlider({
    super.key,
    required this.temperatureThreshold,
    required this.onTemperatureChanged,
  });

  static const List<double> temperatureValues = TemperatureConstants.sliderValues;
  static const List<String> temperatureLabels = TemperatureConstants.sliderLabels;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'Включать, когда температура в салоне ниже ${temperatureThreshold.toInt()}°C',
          style: context.textStyle.paragraph2.copyWith(fontWeight: FontWeight.w500),
        ),
        const SizedBox(height: 12),
        _buildSlider(context),
        const SizedBox(height: 6),
        _buildTemperatureLabels(context),
      ],
    );
  }

  Widget _buildSlider(BuildContext context) {
    return SizedBox(
      height: 32,
      child: SliderTheme(
        data: SliderTheme.of(context).copyWith(
          activeTrackColor: context.themeColors.primary,
          inactiveTrackColor: context.themeColors.sliderInactiveTrack,
          thumbColor: context.themeColors.primary,
          thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 10),
          trackHeight: 8,
          overlayShape: const RoundSliderOverlayShape(overlayRadius: 16),
        ),
        child: Slider(
          value: temperatureThreshold,
          min: TemperatureConstants.sliderMin,
          max: TemperatureConstants.sliderMax,
          divisions: TemperatureConstants.sliderDivisions,
          onChanged: onTemperatureChanged,
        ),
      ),
    );
  }

  Widget _buildTemperatureLabels(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: temperatureLabels.map((label) {
        return Text(label, style: context.textStyle.paragraph3);
      }).toList(),
    );
  }
}
