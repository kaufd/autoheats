// FILE: test/_helpers/logger_test_sink.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Test harness для M-LOGGER: синхронно собирает строки Logger в буфер.
//   SCOPE: installTestSink(buffer), dispose() для восстановления предыдущего sink.
//   DEPENDS: M-LOGGER
//   LINKS: V-M-LOGGER, harness-4
//   ROLE: TEST
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   LoggerTestSink - disposable harness над Logger.installTestSink
//   lines - буфер записанных строк
//   dispose - восстановить предыдущий sink
// END_MODULE_MAP

import 'package:autoheat/src/utils/logger.dart';

class LoggerTestSink {
  final List<String> lines = <String>[];
  late final void Function() _restore;

  LoggerTestSink() {
    _restore = Logger.installTestSink(lines);
  }

  void dispose() {
    _restore();
  }
}
