// FILE: lib/src/utils/logger.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Единая точка структурированного логирования без сторонних библиотек.
//   SCOPE: Logger.debug/info/warn/error, формат [Module][fn][BLOCK], sync sink,
//          test-sink harness и redaction rawCarPropertyValue для non-debug.
//   DEPENDS: none
//   LINKS: M-LOGGER, V-M-LOGGER
//   ROLE: RUNTIME
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   LogLevel - уровни debug/info/warn/error
//   LogSink - синхронный sink строки лога
//   Logger - статический фасад логирования
//   Logger.defaultSink - production sink через print()
//   Logger.installTestSink - временно подменить sink на List.add и вернуть restore callback
//   Logger.debug/info/warn/error - запись структурированной строки
//   _write - сборка строки + защитный catch вокруг sink
//   _formatPrefix/_formatFields/_safeSegment/_redactFieldValue - детали форматирования
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.0.0 - Phase-3 step-1: создан M-LOGGER с redaction и test sink]
// END_CHANGE_SUMMARY

enum LogLevel { debug, info, warn, error }

typedef LogSink = void Function(String message);

class Logger {
  static void defaultSink(String message) {
    // ignore: avoid_print -- M-LOGGER intentionally wraps print() without third-party deps.
    print(message);
  }

  static LogSink _sink = defaultSink;

  Logger._();

  // START_CONTRACT: installTestSink
  //   PURPOSE: Подменить sink на синхронную запись в тестовый буфер.
  //   INPUTS: { buffer: List<String> }
  //   OUTPUTS: { void Function() - restore previous sink }
  //   SIDE_EFFECTS: Меняет глобальный Logger sink до вызова restore.
  //   LINKS: M-LOGGER, V-M-LOGGER, harness-4
  // END_CONTRACT: installTestSink
  static void Function() installTestSink(List<String> buffer) {
    final previousSink = _sink;
    _sink = buffer.add;
    return () {
      _sink = previousSink;
    };
  }

  // START_CONTRACT: debug
  //   PURPOSE: Записать debug-лог; rawCarPropertyValue не маскируется.
  //   INPUTS: { module, fn, block, message, fields? }
  //   OUTPUTS: { void }
  //   SIDE_EFFECTS: Синхронная запись в текущий LogSink.
  //   LINKS: M-LOGGER, V-M-LOGGER
  // END_CONTRACT: debug
  static void debug(
    String module,
    String fn,
    String block,
    String message, [
    Map<String, Object?>? fields,
  ]) {
    _write(LogLevel.debug, module, fn, block, message, fields);
  }

  // START_CONTRACT: info
  //   PURPOSE: Записать info-лог; sensitive fields маскируются.
  //   INPUTS: { module, fn, block, message, fields? }
  //   OUTPUTS: { void }
  //   SIDE_EFFECTS: Синхронная запись в текущий LogSink.
  //   LINKS: M-LOGGER, V-M-LOGGER
  // END_CONTRACT: info
  static void info(
    String module,
    String fn,
    String block,
    String message, [
    Map<String, Object?>? fields,
  ]) {
    _write(LogLevel.info, module, fn, block, message, fields);
  }

  // START_CONTRACT: warn
  //   PURPOSE: Записать warn-лог; sensitive fields маскируются.
  //   INPUTS: { module, fn, block, message, fields? }
  //   OUTPUTS: { void }
  //   SIDE_EFFECTS: Синхронная запись в текущий LogSink.
  //   LINKS: M-LOGGER, V-M-LOGGER
  // END_CONTRACT: warn
  static void warn(
    String module,
    String fn,
    String block,
    String message, [
    Map<String, Object?>? fields,
  ]) {
    _write(LogLevel.warn, module, fn, block, message, fields);
  }

  // START_CONTRACT: error
  //   PURPOSE: Записать error-лог без проброса исключений из sink.
  //   INPUTS: { module, fn, block, message, fields? }
  //   OUTPUTS: { void }
  //   SIDE_EFFECTS: Синхронная запись в текущий LogSink.
  //   LINKS: M-LOGGER, V-M-LOGGER
  // END_CONTRACT: error
  static void error(
    String module,
    String fn,
    String block,
    String message, [
    Map<String, Object?>? fields,
  ]) {
    _write(LogLevel.error, module, fn, block, message, fields);
  }

  // START_BLOCK_WRITE
  static void _write(
    LogLevel level,
    String module,
    String fn,
    String block,
    String message,
    Map<String, Object?>? fields,
  ) {
    final prefix = _formatPrefix(module, fn, block);
    final fieldText = _formatFields(level, fields);
    final line = fieldText.isEmpty
        ? '$prefix $message'
        : '$prefix $message | $fieldText';

    try {
      _sink(line);
    } catch (_) {
      // Логирование не должно ломать runtime-поток автомобиля или тестовый изолят.
    }
  }
  // END_BLOCK_WRITE

  static String _formatPrefix(String module, String fn, String block) {
    return '[${_safeSegment(module)}][${_safeSegment(fn)}][${_safeSegment(block)}]';
  }

  static String _safeSegment(String value) {
    final trimmed = value.trim();
    return trimmed.isEmpty ? 'unknown' : trimmed;
  }

  static String _formatFields(LogLevel level, Map<String, Object?>? fields) {
    if (fields == null || fields.isEmpty) return '';

    return fields.entries
        .map((entry) =>
            '${entry.key}=${_redactFieldValue(level, entry.key, entry.value)}')
        .join(', ');
  }

  static Object? _redactFieldValue(LogLevel level, String key, Object? value) {
    if (level != LogLevel.debug && key == 'rawCarPropertyValue') {
      return '***';
    }
    return value;
  }
}
