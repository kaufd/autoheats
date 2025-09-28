import 'package:autoheat/src/cubit/mode_cubit.dart';
import 'package:autoheat/src/cubit/mode_state_cubit.dart';
import 'package:autoheat/src/extensions/context_extensions.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

class TemperatureSimulator extends StatefulWidget {
  const TemperatureSimulator({super.key});

  @override
  State<TemperatureSimulator> createState() => _TemperatureSimulatorState();
}

class _TemperatureSimulatorState extends State<TemperatureSimulator> {
  double _currentTemp = 5.0;

  @override
  Widget build(BuildContext context) {
    return BlocBuilder<ModeCubit, ModesState>(
      builder: (context, state) {
        final cubit = context.read<ModeCubit>();
        final cabinTemp = cubit.getCabinTemperature();

        return Card(
          margin: const EdgeInsets.all(16),
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Симулятор температуры салона',
                  style: context.textStyle.heading1,
                ),
                const SizedBox(height: 16),
                Row(
                  children: [
                    Text(
                      'Текущая температура: ',
                      style: context.textStyle.paragraph1,
                    ),
                    Text(
                      '${cabinTemp?.toStringAsFixed(1) ?? "Не установлена"}°C',
                      style: context.textStyle.paragraph1.copyWith(
                        fontWeight: FontWeight.bold,
                        color: _getTemperatureColor(cabinTemp ?? _currentTemp),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 16),
                Text(
                  'Установить температуру: ${_currentTemp.toStringAsFixed(1)}°C',
                  style: context.textStyle.paragraph1,
                ),
                Slider(
                  value: _currentTemp,
                  min: -15.0,
                  max: 10.0,
                  divisions: 25,
                  onChanged: (value) {
                    setState(() {
                      _currentTemp = value;
                    });
                    cubit.setCabinTemperature(value);
                  },
                ),
                const SizedBox(height: 16),
                Wrap(
                  spacing: 8,
                  children: [
                    _buildTempButton('Очень холодно', -15.0),
                    _buildTempButton('Холодно', -8.0),
                    _buildTempButton('Прохладно', -2.0),
                    _buildTempButton('Тепло', 5.0),
                    _buildTempButton('Жарко', 10.0),
                  ],
                ),
                const SizedBox(height: 16),
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: context.themeColors.primary.withAlpha(30),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Новый алгоритм автоматического подогрева:',
                        style: context.textStyle.paragraph1.copyWith(
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      const SizedBox(height: 8),
                      Text('• > +10°C: Выключено', style: context.textStyle.paragraph1),
                      Text('• +5°C до +10°C: 3→2→1 (2+2+6=10 мин)',
                          style: context.textStyle.paragraph1),
                      Text('• 0°C до +5°C: 3→2→1 (4+2+8=14 мин)',
                          style: context.textStyle.paragraph1),
                      Text('• -5°C до 0°C: 3→2→1 (6+4+10=20 мин)',
                          style: context.textStyle.paragraph1),
                      Text('• -10°C до -5°C: 3→2→1 (8+6+12=26 мин)',
                          style: context.textStyle.paragraph1),
                      Text('• < -10°C: 3→2→1 (10+8+15=33 мин)',
                          style: context.textStyle.paragraph1),
                      const SizedBox(height: 8),
                      Text('🔥 Прогрессивный подогрев: 3→2→1',
                          style: context.textStyle.paragraph1.copyWith(
                            fontStyle: FontStyle.italic,
                            color: Colors.orange,
                          )),
                    ],
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  Widget _buildTempButton(String label, double temp) {
    return ElevatedButton(
      onPressed: () {
        setState(() {
          _currentTemp = temp;
        });
        context.read<ModeCubit>().setCabinTemperature(temp);
      },
      style: ElevatedButton.styleFrom(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      ),
      child: Text(label),
    );
  }

  Color _getTemperatureColor(double temp) {
    if (temp <= -8) return Colors.blue;
    if (temp <= -2) return Colors.lightBlue;
    if (temp <= 4) return Colors.orange;
    return Colors.red;
  }
}
