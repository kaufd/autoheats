// FILE: lib/src/presentation/screens/debug/logs_screen.dart
// VERSION: 1.0.0
// Diagnostic UI: живой просмотр последних строк LogRingBuffer
// (multiplexed копия всего, что Logger пишет в print).

import 'package:autoheat/src/extensions/context_extensions.dart';
import 'package:autoheat/src/utils/logger.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class LogsScreen extends StatefulWidget {
  const LogsScreen({super.key});

  @override
  State<LogsScreen> createState() => _LogsScreenState();
}

class _LogsScreenState extends State<LogsScreen> {
  late final ScrollController _scrollController;
  late final VoidCallback _bufferListener;
  bool _autoScroll = true;

  @override
  void initState() {
    super.initState();
    _scrollController = ScrollController();
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

  @override
  Widget build(BuildContext context) {
    final lines = LogRingBuffer.instance.lines;

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _buildToolbar(context, lines.length),
          const SizedBox(height: 8),
          Expanded(
            child: lines.isEmpty
                ? _buildEmpty(context)
                : _buildList(context, lines),
          ),
        ],
      ),
    );
  }

  Widget _buildToolbar(BuildContext context, int count) {
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
    // Logger пишет уровни префиксом [Module][fn][BLOCK], а ключевые слова
    // error/fallback/warn/ignored — это уже в теле message. Подсвечиваем
    // эвристически.
    final lower = line.toLowerCase();
    if (lower.contains(' error') || lower.contains('|error') ||
        lower.contains('exception') || lower.contains('failed')) {
      return Colors.redAccent.shade100;
    }
    if (lower.contains('warn') || lower.contains('fallback') ||
        lower.contains('ignored')) {
      return Colors.orangeAccent.shade100;
    }
    return context.themeColors.textBody;
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
