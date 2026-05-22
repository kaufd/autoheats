// FILE: test/unit/logger_test.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Unit-тесты M-LOGGER: формат [Module][fn][BLOCK], redaction,
//            sync sink и запрет сторонних logging/debugPrint зависимостей.
//   SCOPE: 8 сценариев V-M-LOGGER + forbidden sink-throw safety.
//   DEPENDS: M-LOGGER
//   LINKS: V-M-LOGGER
//   ROLE: TEST
//   MAP_MODE: LOCALS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   ThrowingStringList - ListBase, бросает на add для forbidden-3
//   main - сценарии V-M-LOGGER 1..8
// END_MODULE_MAP

import 'dart:collection';
import 'dart:io';

import 'package:autoheat/src/utils/logger.dart';
import 'package:flutter_test/flutter_test.dart';

import '../_helpers/logger_test_sink.dart';

class ThrowingStringList extends ListBase<String> {
  final List<String> _items = <String>[];

  @override
  int get length => _items.length;

  @override
  set length(int newLength) {
    _items.length = newLength;
  }

  @override
  String operator [](int index) => _items[index];

  @override
  void operator []=(int index, String value) {
    _items[index] = value;
  }

  @override
  void add(String element) {
    throw StateError('sink failed');
  }
}

void main() {
  // START_BLOCK_FORMAT_AND_LEVELS
  test('scenario-1: info пишет [Module][fn][BLOCK] message | fields', () {
    final sink = LoggerTestSink();
    addTearDown(sink.dispose);

    Logger.info(
      'HvacService',
      'setSeatHeatLevel',
      'BLOCK_SET_SEAT_HEAT_LEVEL',
      'ok',
      {'level': 2},
    );

    expect(sink.lines, [
      '[HvacService][setSeatHeatLevel][BLOCK_SET_SEAT_HEAT_LEVEL] ok | level=2',
    ]);
  });

  test('scenario-2: installTestSink ловит debug/info/warn/error синхронно', () {
    final sink = LoggerTestSink();
    addTearDown(sink.dispose);

    Logger.debug('M', 'debugFn', 'BLOCK_DEBUG', 'debug');
    Logger.info('M', 'infoFn', 'BLOCK_INFO', 'info');
    Logger.warn('M', 'warnFn', 'BLOCK_WARN', 'warn');
    Logger.error('M', 'errorFn', 'BLOCK_ERROR', 'error');

    expect(sink.lines, [
      '[M][debugFn][BLOCK_DEBUG] debug',
      '[M][infoFn][BLOCK_INFO] info',
      '[M][warnFn][BLOCK_WARN] warn',
      '[M][errorFn][BLOCK_ERROR] error',
    ]);
  });
  // END_BLOCK_FORMAT_AND_LEVELS

  // START_BLOCK_REDACTION
  test('scenario-3: non-debug маскирует rawCarPropertyValue', () {
    final sink = LoggerTestSink();
    addTearDown(sink.dispose);

    Logger.info('HvacService', 'onHvacChangeEvent',
        'BLOCK_HANDLE_TEMPERATURE_EVENT', 'raw', {'rawCarPropertyValue': 42});

    expect(sink.lines.single, contains('rawCarPropertyValue=***'));
    expect(sink.lines.single, isNot(contains('rawCarPropertyValue=42')));
  });

  test('scenario-4: debug не маскирует rawCarPropertyValue', () {
    final sink = LoggerTestSink();
    addTearDown(sink.dispose);

    Logger.debug('HvacService', 'onHvacChangeEvent',
        'BLOCK_HANDLE_TEMPERATURE_EVENT', 'raw', {'rawCarPropertyValue': 42});

    expect(sink.lines.single, contains('rawCarPropertyValue=42'));
  });
  // END_BLOCK_REDACTION

  // START_BLOCK_FAILURE_INVARIANTS
  test('scenario-5: пустые module/fn/block заменяются на unknown', () {
    final sink = LoggerTestSink();
    addTearDown(sink.dispose);

    Logger.info('', '', '', 'msg');

    expect(sink.lines, ['[unknown][unknown][unknown] msg']);
  });

  test('scenario-6: null field форматируется как k=null без NPE', () {
    final sink = LoggerTestSink();
    addTearDown(sink.dispose);

    Logger.info('M', 'fn', 'BLOCK', 'msg', {'k': null});

    expect(sink.lines.single, '[M][fn][BLOCK] msg | k=null');
  });

  test('scenario-7: порядок fields сохраняется', () {
    final sink = LoggerTestSink();
    addTearDown(sink.dispose);

    Logger.info('M', 'fn', 'BLOCK', 'msg', {'a': 1, 'b': 2, 'c': 3});

    expect(sink.lines.single, '[M][fn][BLOCK] msg | a=1, b=2, c=3');
  });

  test('forbidden-3: Logger.error не бросает, если sink бросает', () {
    final restore = Logger.installTestSink(ThrowingStringList());
    addTearDown(restore);

    expect(
      () => Logger.error('M', 'fn', 'BLOCK', 'msg'),
      returnsNormally,
    );
  });
  // END_BLOCK_FAILURE_INVARIANTS

  // START_BLOCK_IMPORT_CONSTRAINTS
  test('scenario-8: logger.dart не импортирует logging/debugPrint зависимости',
      () {
    final source = File('lib/src/utils/logger.dart').readAsStringSync();

    expect(source, isNot(contains('package:logger')));
    expect(source, isNot(contains('package:logging')));
    expect(source, isNot(contains('package:flutter/foundation.dart')));
    expect(source, contains('print('));
  });
  // END_BLOCK_IMPORT_CONSTRAINTS
}
