// FILE: lib/src/presentation/screens/debug/temp_injector_screen.dart
// VERSION: 1.0.0
// Diagnostic UI: инжекция температуры в AutoHeatService для тестирования
// auto/preset-режимов летом, когда естественная температура салона выше
// порогов TemperatureRange.off (>=10°C) или пресета (<=15°C по слайдеру).

import 'package:autoheat/src/extensions/context_extensions.dart';
import 'package:autoheat/src/services/auto_heat_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class TempInjectorScreen extends StatefulWidget {
  const TempInjectorScreen({super.key});

  @override
  State<TempInjectorScreen> createState() => _TempInjectorScreenState();
}

class _TempInjectorScreenState extends State<TempInjectorScreen> {
  // Слайдер с дискретными значениями — повторяет UX слайдера threshold пресета.
  static const List<double> _quickValues = [0, 5, 10, 15, 20, 25, 30];

  double _sliderValue = 15;
  final TextEditingController _exactController = TextEditingController();
  double? _lastApplied;

  @override
  void dispose() {
    _exactController.dispose();
    super.dispose();
  }

  void _applySlider() {
    AutoHeatService().setTemperature(_sliderValue);
    setState(() {
      _lastApplied = _sliderValue;
    });
    _showFeedback(_sliderValue);
  }

  void _applyExact() {
    final raw = _exactController.text.trim().replaceAll(',', '.');
    final value = double.tryParse(raw);
    if (value == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: const Text('Введите число (например, -2.5)'),
          backgroundColor: context.themeColors.primary,
        ),
      );
      return;
    }
    AutoHeatService().setTemperature(value);
    setState(() {
      _lastApplied = value;
    });
    _showFeedback(value);
  }

  void _showFeedback(double value) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        duration: const Duration(seconds: 1),
        content: Text(
          'Inject ${value.toStringAsFixed(1)}°C → AutoHeatService',
          style: TextStyle(color: context.themeColors.textButtonSelected),
        ),
        backgroundColor: context.themeColors.primary,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final current = AutoHeatService().currentTemperature;
    return SingleChildScrollView(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _buildStatusRow(context, current),
          const SizedBox(height: 24),
          Text(
            'Быстрый выбор температуры (°C)',
            style: context.textStyle.textSettings,
          ),
          const SizedBox(height: 12),
          _buildQuickSlider(context),
          const SizedBox(height: 8),
          _buildQuickLabels(context),
          const SizedBox(height: 12),
          Align(
            alignment: Alignment.centerLeft,
            child: _buildPrimaryButton(
              context,
              label: 'Применить ${_sliderValue.toStringAsFixed(0)}°C',
              onPressed: _applySlider,
            ),
          ),
          const SizedBox(height: 32),
          Text(
            'Произвольное значение',
            style: context.textStyle.textSettings,
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              SizedBox(
                width: 200,
                child: TextField(
                  controller: _exactController,
                  keyboardType: const TextInputType.numberWithOptions(
                    decimal: true,
                    signed: true,
                  ),
                  inputFormatters: [
                    FilteringTextInputFormatter.allow(
                      RegExp(r'[\-0-9.,]'),
                    ),
                  ],
                  decoration: InputDecoration(
                    hintText: 'например, -2.5',
                    border: const OutlineInputBorder(),
                    contentPadding: const EdgeInsets.symmetric(
                      horizontal: 12,
                      vertical: 8,
                    ),
                    suffixText: '°C',
                  ),
                  style: context.textStyle.paragraph1,
                ),
              ),
              const SizedBox(width: 16),
              _buildPrimaryButton(
                context,
                label: 'Применить',
                onPressed: _applyExact,
              ),
            ],
          ),
          const SizedBox(height: 32),
          if (_lastApplied != null)
            Text(
              'Последний инжект: ${_lastApplied!.toStringAsFixed(1)}°C',
              style: context.textStyle.paragraph2.copyWith(
                color: context.themeColors.textMuted,
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildStatusRow(BuildContext context, double? current) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      decoration: BoxDecoration(
        color: context.themeColors.primary.withAlpha(30),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: context.themeColors.primary.withAlpha(100),
          width: 1,
        ),
      ),
      child: Row(
        children: [
          Icon(Icons.thermostat,
              color: context.themeColors.primary, size: 24),
          const SizedBox(width: 8),
          Text(
            'AutoHeatService.currentTemperature: ',
            style: context.textStyle.paragraph1,
          ),
          Text(
            current == null ? '—' : '${current.toStringAsFixed(1)}°C',
            style: context.textStyle.paragraph1.copyWith(
              fontWeight: FontWeight.bold,
              color: context.themeColors.primary,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildQuickSlider(BuildContext context) {
    return SliderTheme(
      data: SliderTheme.of(context).copyWith(
        activeTrackColor: context.themeColors.primary,
        inactiveTrackColor: context.themeColors.sliderInactiveTrack,
        thumbColor: context.themeColors.primary,
        thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 12),
        trackHeight: 8,
        overlayShape: const RoundSliderOverlayShape(overlayRadius: 18),
      ),
      child: Slider(
        value: _sliderValue,
        min: _quickValues.first,
        max: _quickValues.last,
        divisions: _quickValues.length - 1,
        label: '${_sliderValue.toStringAsFixed(0)}°C',
        onChanged: (value) {
          setState(() {
            _sliderValue = value;
          });
        },
      ),
    );
  }

  Widget _buildQuickLabels(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: _quickValues
          .map((value) => Text(
                '${value.toStringAsFixed(0)}°C',
                style: context.textStyle.paragraph3,
              ))
          .toList(),
    );
  }

  Widget _buildPrimaryButton(
    BuildContext context, {
    required String label,
    required VoidCallback onPressed,
  }) {
    return TextButton(
      onPressed: onPressed,
      style: ButtonStyle(
        foregroundColor:
            WidgetStatePropertyAll(context.themeColors.textButtonSelected),
        backgroundColor: WidgetStatePropertyAll(context.themeColors.primary),
        padding: const WidgetStatePropertyAll(
          EdgeInsets.symmetric(horizontal: 24, vertical: 12),
        ),
      ),
      child: Text(label),
    );
  }
}
