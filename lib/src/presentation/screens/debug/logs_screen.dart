// FILE: lib/src/presentation/screens/debug/logs_screen.dart
// VERSION: 1.1.0
// Diagnostic UI: живой просмотр последних строк LogRingBuffer (multiplexed
// копия всего, что Logger пишет в print) + sidebar инжектора температуры
// в AutoHeatService (для проверки auto/preset-режимов летом).

import 'package:autoheat/src/extensions/context_extensions.dart';
import 'package:autoheat/src/services/auto_heat_service.dart';
import 'package:autoheat/src/utils/logger.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class LogsScreen extends StatefulWidget {
  const LogsScreen({super.key});

  @override
  State<LogsScreen> createState() => _LogsScreenState();
}

class _LogsScreenState extends State<LogsScreen> {
  // Quick-shortcut значения для инжектора. Не слайдер — обычные chips,
  // потому что в head-unit landscape ширина sidebar'а ограничена.
  static const List<double> _quickValues = [0, 5, 10, 15, 20, 25, 30];

  late final ScrollController _scrollController;
  late final VoidCallback _bufferListener;
  late final TextEditingController _injectController;
  bool _autoScroll = true;
  double? _lastInjected;

  @override
  void initState() {
    super.initState();
    _scrollController = ScrollController();
    _injectController = TextEditingController();
    _bufferListener = () {
      if (!mounted) return;
      setState(() {});
      if (_autoScroll) {
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (!_scrollController.hasClients) return;
          _scrollController.jumpTo(_scrollController.position.maxScrollExtent);
        });
      }
    };
    LogRingBuffer.instance.addListener(_bufferListener);
  }

  @override
  void dispose() {
    LogRingBuffer.instance.removeListener(_bufferListener);
    _scrollController.dispose();
    _injectController.dispose();
    super.dispose();
  }

  Future<void> _copyAll() async {
    final lines = LogRingBuffer.instance.lines;
    final text = lines.join('\n');
    await Clipboard.setData(ClipboardData(text: text));
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        duration: const Duration(seconds: 1),
        content: Text(
          'Скопировано ${lines.length} строк в clipboard',
          style: TextStyle(color: context.themeColors.textButtonSelected),
        ),
        backgroundColor: context.themeColors.primary,
      ),
    );
  }

  void _clear() {
    LogRingBuffer.instance.clear();
  }

  void _inject(double value) {
    AutoHeatService().setTemperature(value);
    setState(() {
      _lastInjected = value;
    });
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

  void _injectFromInput() {
    final raw = _injectController.text.trim().replaceAll(',', '.');
    final value = double.tryParse(raw);
    if (value == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          duration: const Duration(seconds: 1),
          content: const Text('Введите число (например, -2.5)'),
          backgroundColor: context.themeColors.primary,
        ),
      );
      return;
    }
    _inject(value);
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Expanded(child: _buildLogsPane(context)),
          const SizedBox(width: 16),
          SizedBox(
            width: 320,
            child: _buildInjectorSidebar(context),
          ),
        ],
      ),
    );
  }

  // ---------- Logs pane ----------

  Widget _buildLogsPane(BuildContext context) {
    final lines = LogRingBuffer.instance.lines;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _buildLogsToolbar(context, lines.length),
        const SizedBox(height: 8),
        Expanded(
          child: lines.isEmpty
              ? _buildEmpty(context)
              : _buildList(context, lines),
        ),
      ],
    );
  }

  Widget _buildLogsToolbar(BuildContext context, int count) {
    return Row(
      children: [
        Text(
          '$count / ${LogRingBuffer.capacity}',
          style: context.textStyle.paragraph2.copyWith(
            color: context.themeColors.textMuted,
          ),
        ),
        const SizedBox(width: 16),
        Row(
          children: [
            Checkbox(
              value: _autoScroll,
              onChanged: (value) {
                setState(() {
                  _autoScroll = value ?? false;
                });
              },
            ),
            Text(
              'Auto-scroll',
              style: context.textStyle.paragraph2,
            ),
          ],
        ),
        const Spacer(),
        _buildSecondaryButton(context, 'Скопировать', _copyAll),
        const SizedBox(width: 8),
        _buildSecondaryButton(context, 'Очистить', _clear),
      ],
    );
  }

  Widget _buildEmpty(BuildContext context) {
    return Center(
      child: Text(
        'Логов пока нет',
        style: context.textStyle.paragraph2.copyWith(
          color: context.themeColors.textMuted,
        ),
      ),
    );
  }

  Widget _buildList(BuildContext context, List<String> lines) {
    return Container(
      decoration: BoxDecoration(
        color: context.themeColors.primary.withAlpha(20),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(
          color: context.themeColors.primary.withAlpha(80),
          width: 1,
        ),
      ),
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
      child: Scrollbar(
        controller: _scrollController,
        child: ListView.builder(
          controller: _scrollController,
          itemCount: lines.length,
          itemBuilder: (_, index) {
            final line = lines[index];
            return Padding(
              padding: const EdgeInsets.symmetric(vertical: 2),
              child: SelectableText(
                line,
                style: TextStyle(
                  fontFamily: 'monospace',
                  fontSize: 11,
                  color: _colorFor(context, line),
                ),
              ),
            );
          },
        ),
      ),
    );
  }

  Color _colorFor(BuildContext context, String line) {
    final lower = line.toLowerCase();
    if (lower.contains(' error') ||
        lower.contains('|error') ||
        lower.contains('exception') ||
        lower.contains('failed')) {
      return Colors.redAccent.shade100;
    }
    if (lower.contains('warn') ||
        lower.contains('fallback') ||
        lower.contains('ignored')) {
      return Colors.orangeAccent.shade100;
    }
    return context.themeColors.textBody;
  }

  // ---------- Injector sidebar ----------

  Widget _buildInjectorSidebar(BuildContext context) {
    final current = AutoHeatService().currentTemperature;
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: context.themeColors.primary.withAlpha(20),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: context.themeColors.primary.withAlpha(100),
          width: 1,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            'Inject temperature',
            style: context.textStyle.textSettings,
          ),
          const SizedBox(height: 8),
          Text(
            'AutoHeatService.setTemperature() — тот же путь, что и реальный sensor event.',
            style: context.textStyle.paragraph3.copyWith(
              color: context.themeColors.textMuted,
            ),
          ),
          const SizedBox(height: 16),
          _buildCurrentRow(context, current),
          const SizedBox(height: 16),
          Text('Быстрые значения', style: context.textStyle.paragraph2),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: _quickValues.map((v) => _buildQuickChip(context, v)).toList(),
          ),
          const SizedBox(height: 20),
          Text('Произвольное значение', style: context.textStyle.paragraph2),
          const SizedBox(height: 8),
          TextField(
            controller: _injectController,
            keyboardType: const TextInputType.numberWithOptions(
              decimal: true,
              signed: true,
            ),
            inputFormatters: [
              FilteringTextInputFormatter.allow(RegExp(r'[\-0-9.,]')),
            ],
            decoration: const InputDecoration(
              hintText: 'например, -2.5',
              border: OutlineInputBorder(),
              contentPadding: EdgeInsets.symmetric(
                horizontal: 12,
                vertical: 8,
              ),
              suffixText: '°C',
            ),
            style: context.textStyle.paragraph1,
            onSubmitted: (_) => _injectFromInput(),
          ),
          const SizedBox(height: 12),
          Align(
            alignment: Alignment.centerRight,
            child: _buildPrimaryButton(context, 'Применить', _injectFromInput),
          ),
          if (_lastInjected != null) ...[
            const SizedBox(height: 16),
            Text(
              'Последний инжект: ${_lastInjected!.toStringAsFixed(1)}°C',
              style: context.textStyle.paragraph3.copyWith(
                color: context.themeColors.textMuted,
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildCurrentRow(BuildContext context, double? current) {
    return Row(
      children: [
        Icon(Icons.thermostat,
            color: context.themeColors.primary, size: 20),
        const SizedBox(width: 6),
        Expanded(
          child: Text(
            'currentTemperature',
            style: context.textStyle.paragraph2,
          ),
        ),
        Text(
          current == null ? '—' : '${current.toStringAsFixed(1)}°C',
          style: context.textStyle.paragraph2.copyWith(
            fontWeight: FontWeight.bold,
            color: context.themeColors.primary,
          ),
        ),
      ],
    );
  }

  Widget _buildQuickChip(BuildContext context, double value) {
    return InkWell(
      onTap: () => _inject(value),
      borderRadius: BorderRadius.circular(8),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
        decoration: BoxDecoration(
          color: context.themeColors.primary.withValues(alpha: 0.18),
          borderRadius: BorderRadius.circular(8),
          border: Border.all(
            color: context.themeColors.primary.withValues(alpha: 0.6),
            width: 1,
          ),
        ),
        child: Text(
          '${value.toStringAsFixed(0)}°C',
          style: context.textStyle.paragraph2.copyWith(
            color: context.themeColors.primary,
            fontWeight: FontWeight.w600,
          ),
        ),
      ),
    );
  }

  Widget _buildPrimaryButton(
    BuildContext context,
    String label,
    VoidCallback onPressed,
  ) {
    return TextButton(
      onPressed: onPressed,
      style: ButtonStyle(
        foregroundColor:
            WidgetStatePropertyAll(context.themeColors.textButtonSelected),
        backgroundColor: WidgetStatePropertyAll(context.themeColors.primary),
        padding: const WidgetStatePropertyAll(
          EdgeInsets.symmetric(horizontal: 20, vertical: 10),
        ),
      ),
      child: Text(label),
    );
  }

  Widget _buildSecondaryButton(
    BuildContext context,
    String label,
    VoidCallback onPressed,
  ) {
    return TextButton(
      onPressed: onPressed,
      style: ButtonStyle(
        foregroundColor:
            WidgetStatePropertyAll(context.themeColors.textButtonSelected),
        backgroundColor: WidgetStatePropertyAll(context.themeColors.primary),
        padding: const WidgetStatePropertyAll(
          EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        ),
      ),
      child: Text(label),
    );
  }
}
