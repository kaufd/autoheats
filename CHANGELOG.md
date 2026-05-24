# Changelog

Все заметные изменения в проекте AutoHeat. Формат следует
[Keep a Changelog](https://keepachangelog.com/ru/1.1.0/),
проект придерживается [Semantic Versioning](https://semver.org/lang/ru/).

Каждая секция `## [X.Y.Z]` соответствует git-тегу `vX.Y.Z` и
автоматически копируется в описание GitHub Release при пуше тега
(см. `.github/workflows/release.yml`).

## [Unreleased]

## [0.1.0] - 2026-05-25

### Добавлено
- Полностью переработанная архитектура управления подогревом сидений:
  три режима (`manual`, `presets`, `auto`) и единый маршрут
  `ModeCubit → AutoHeatService → HvacService` через GetIt DI.
- `AutoHeatService` с адаптивным step-down по температуре, max-timer
  safety-net и plan-key guard (фикс «бесконечного перезапуска уровня 3»
  после завершения каскада в стабильно холодном салоне).
- Пресеты с фиксированным каскадом длительностей и temperature
  threshold gate на старте: подскок температуры не прерывает уже
  идущий каскад.
- Foreground-service `flutter_background_service` для работы при
  свёрнутом приложении; авто-shutdown сидений по ignition OFF;
  retry-backoff при сбоях старта (`BackgroundRuntimeController`).
- Debug-режим (длинный тап по индикатору температуры салона):
  отдельная вкладка «Логи» с живым in-memory ring buffer на 500 строк
  и sidebar-инжектором температуры в `AutoHeatService` для проверки
  алгоритма без сенсорных событий. При выключении debug-режима
  injected значение автоматически вытесняется реальной температурой,
  буфер логов очищается.
- GRACE-разметка модулей: `MODULE_CONTRACT`, `MODULE_MAP`,
  `CONTRACT`/`BLOCK` маркеры в Logger-выводе.

### Изменено
- Android manifest: переносим минимально необходимый набор
  `android.car.permission.*` из `autoheat_old`
  (`CONTROL_CAR_CLIMATE`, `CAR_POWER`, `CAR_POWERTRAIN`,
  `CAR_VENDOR_EXTENSION`, `CAR_INFO`), плюс `RECEIVE_BOOT_COMPLETED`
  для `autoStartOnBoot`, `coagent.permission.SEND_PROTOCOL` и
  `com.wt.airconditioner.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.
- Имя приложения: `AutoHeat v3` (manifest label + `MaterialApp.title`).
- Имя APK на выходе: `AutoHeat-v3.apk` (`outputFileName` в gradle).
- Сборка только под `arm64-v8a` (`--target-platform android-arm64`),
  APK уменьшился с ~53 MB до ~19 MB.
- Удалены неиспользуемые ассеты (`seat.png`, `seat2.png`,
  `seat6 14.12.39.png`, `seat31.avif`, vecteezy SVG) — экономия ~2 MB.

### Исправлено
- `AutoHeatService`: бесконечный перезапуск каскада `3→2→1→0→3→…`
  при стабильной холодной температуре (введён plan-key guard).
- `PresetsTab._onApply` после редактирования активного пресета теперь
  применяет именно draft-настройки, а не stale-объект из state.
- `accessibility_service.dart`: переиспользуется
  `HvacService.androidAutomotivePlugin` вместо создания второго
  `AndroidAutomotivePlugin` — иначе `MethodCallHandler` канала
  `android_automotive_plugin` перезаписывался и UI-изолят терял
  cabin-temperature события.
- `HvacService.initialize()`: in-flight guard `_initInFlight` —
  параллельные вызовы (background_service + ModeCubit warmup +
  CabinTemperatureCubit) делят один `connect()` вместо гонки.
- `ModeCubit._initializeHeatModes`: откат `presets → manual+0` при
  невалидном persisted preset id теперь идёт через публичный
  `setHeatLevel(0)` — HVAC получает level=0 (раньше состояние и
  железо могли расходиться).
- `AppContent.initState`: pre-warm `PresetCubit.loadAllPresets()`,
  чтобы тап сегмента «Пресеты» в `ModeToggler` сразу видел активный
  пресет, а не падал на пустой state.
- `ModeCubit.toggleHeatLevel`: уход из `presets`/`auto` в `manual`
  идёт через единый `setMode` (Logger marker `BLOCK_SET_MODE`,
  единый путь `_manageAutoHeat`).
- `PresetService.getPresets`: `Logger.warn` при битом JSON в
  SharedPreferences — раньше пресеты молча терялись.
- Поиск пресета по id в `presets_tab` сужен до `selectedUser` —
  защита от теоретической коллизии id между driver и passenger.

### Внутреннее
- 137 unit/widget/integration тестов, включая
  `test/scenarios/walkthrough_log_test.dart` — log-driven сценарии
  работы `auto`/`preset` режимов и переключений между ними,
  читаемые через `flutter test --reporter expanded`.
- Логи через единый `Logger` фасад, multiplexed в
  `LogRingBuffer.instance` для debug UI.
- Анализ статикой: `flutter analyze` — без замечаний.
