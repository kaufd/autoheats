# AutoHeat

Flutter-приложение для автоматического управления подогревом сидений в автомобиле Changan через головное устройство на Android Automotive OS.

Текущая версия: **1.0.0** (см. `pubspec.yaml`, `CHANGELOG.md`).

## Контекст и назначение

- **Платформа выполнения**: головное устройство (head unit) автомобиля Changan на базе Android Automotive. Целевые модели — UNI-S, CS55 Plus (MediaTek MT8666, AAOS-build с суффиксом `_64_car`). Не телефон, не планшет.
- **Происхождение**: переработка предыдущего проекта `/Users/kaufd/_Projects/open-source/changan/autoheat_old` (там плагин подключался как git-зависимость, в нынешнем проекте — переехал в `packages/` как path-зависимость).
- **Назначение приложения**: вместо ручного нажатия кнопок подогрева сидений приложение делает это автоматически на основе температуры в салоне и заданных пресетов, либо по расписанию мощности (3 → 2 → 1 → off).
- **Режимы работы**:
  - `manual` — ручное управление уровнем подогрева;
  - `presets` — заранее сохранённые пользовательские конфигурации с фиксированным каскадом длительностей и temperature-threshold гейтом на старте;
  - `auto` — автоматический режим по датчику температуры салона с адаптивным step-down и max-timer safety-net.

## Аппаратные и платформенные требования

- **OS**: Android Automotive (через `android.car.*` API), `minSdk = 23`. Архитектура — `arm64-v8a` (64-bit ARM, все современные AAOS-головы).
- **Экран**: фиксированное альбомное разрешение головного устройства. UI рассчитан на горизонтальную ориентацию и не должен «плыть» в произвольных размерах — при правках компонентов учитывать `MediaQuery` и проверять на разрешении head unit. Не закладывать поддержку телефонов/планшетов.
- **Подпись APK**: на голове Changan **достаточно debug-keystore** — так же, как делал `autoheat_old`, и так же сейчас (см. `android/app/build.gradle`, `release { signingConfig = signingConfigs.debug }`). Платформенная подпись не требуется. Контракт работы с `android.car.*` определяется набором permissions в `AndroidManifest.xml`.
- **Permissions** (`android/app/src/main/AndroidManifest.xml`):
  - `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS`, `WAKE_LOCK` — фоновый сервис.
  - `RECEIVE_BOOT_COMPLETED` — `autoStartOnBoot` у `flutter_background_service`.
  - `android.car.permission.CONTROL_CAR_CLIMATE`, `CAR_POWER`, `CAR_POWERTRAIN`, `CAR_VENDOR_EXTENSION`, `CAR_INFO` — обязательные для HVAC / ignition / vendor-extension через `AndroidAutomotivePlugin`.
  - `coagent.permission.SEND_PROTOCOL` — vendor-bridge Changan/CoAgent.
  - `com.wt.airconditioner.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` — объявлена и `uses-permission`: на Android 13+ нужна для динамических BroadcastReceiver'ов от системного AirConditioner-сервиса.
  - `lint { disable += "UniquePermission" }` — false-positive по совпадению суффикса permission с AGP-генерируемым namespace'ом приложения.
- **applicationId / namespace**: `ru.kaufd.autoheat`. **applicationLabel / `MaterialApp.title`**: `AutoHeat v3`.

## Технологический стек

- **Flutter** SDK `^3.6.0`, Dart 3. Pin для CI: Flutter `3.41.9` (`.github/workflows/release.yml`).
- **State management**: `flutter_bloc` (Cubit) + `equatable`.
- **DI**: `get_it` (см. `lib/src/di/service_locator.dart`), идемпотентный per-isolate `setupServiceLocator`.
- **Persistence**: `shared_preferences` для всех пользовательских настроек, пресетов, режимов, выбранной темы, debug-флага и порога автоподогрева.
- **Фон**: `flutter_background_service` (foreground-service с уведомлением `my_foreground`, id `888`).
- **Кодогенерация**: `json_serializable` + `build_runner` (`*.g.dart` для моделей).
- **Линтер**: `flutter_lints`.

## Структура каталогов

```
lib/
  main.dart                    — точка входа, поднимает DI, темы, accessibility и background service
  src/
    app_enums.dart             — HeatMode, UserType + расширения
    cubit/                     — ModeCubit, ModeStateCubit, CabinTemperatureCubit, PresetCubit, SettingsCubit
    di/                        — service_locator.dart (GetIt), app_bloc_providers.dart
    config/                    — темы, цвета, текстовые стили
    constants/                 — TemperatureConstants и HeatSequence (расписания подогрева по диапазонам)
    models/                    — Preset, ManualHeatSettings (+ codegen *.g.dart)
    services/
      hvac_service.dart                  — обёртка над AndroidAutomotivePlugin + CarHvacManager (in-flight initialize guard)
      auto_heat_service.dart             — синглтон, авто/preset режим, plan-key guard, max-timer
      background_service.dart            — foreground-service onStart, маршрут через HvacService.initialize()
      background_runtime_controller.dart — ignition OFF → shutdown сидений, retry-backoff
      accessibility_service.dart         — регистрация callback handle (использует ТОТ ЖЕ plugin из DI)
      mode_service.dart, preset_service.dart, settings_service.dart
    utils/
      logger.dart              — единый Logger + LogRingBuffer (in-memory кольцевой буфер для debug UI)
    extensions/
    presentation/
      app_content.dart                       — TabController (3 или 4 таба), BlocListener на debugMode
      themes/                                — ThemeCubit, ThemeService, ThemeConfigurator
      ui/                                    — переиспользуемые виджеты (CustomSwitch, AlertDialog, ErrorBlock)
      screens/
        heat/                                — главный экран (Seat, CabinTemperatureDisplay с long-press toggle, ModeToggler)
        presets/                             — редактор + список пресетов
        settings/                            — тема, видимость температуры
        debug/
          logs_screen.dart                   — живой LogRingBuffer + sidebar-инжектор AutoHeatService.setTemperature

packages/
  android_automotive_plugin/   — локальный path-плагин-обёртка над android.car.* API

test/
  unit/                        — модульные тесты сервисов, кубитов и моделей
  widget/                      — widget-тесты UI
  scenarios/walkthrough_log_test.dart — log-driven сценарии auto/preset режимов и переключений
  _helpers/                    — FakeHvacService, FakePlugin, LoggerTestSink

.github/workflows/release.yml  — CI релиз по push'у тега v*
CHANGELOG.md                   — Keep a Changelog формат, секция [X.Y.Z] = body GitHub Release
ARCHITECTURE.md                — диаграммы потоков
```

## Базовая библиотека

Проект использует локальный path-плагин `packages/android_automotive_plugin/`. Исходник — снапшот апстрима с GitHub: `https://github.com/abuharsky/changan_car_flutter_library` (namespace `com.bukharskii.flutter.automotive.android_automotive_plugin` — авторства abuharsky). Локальный слепок добавлен 2025-02-22 коммитом `6f4babc Add android_automotive_plugin as local package` и с тех пор не правился; апстрим тоже стагнирует. Это не форк и не доработка — содержимое плагина полностью соответствует upstream на момент снапшота.

**Почему path, а не git-зависимость** (в `autoheat_old` плагин подключался через `git: url: ...`): upstream фактически заброшен, поэтому детерминированный локальный снапшот защищает сборку от исчезновения/архивации репозитория и работает офлайн. Если когда-нибудь потребуется правка под autoheat — её удобно делать на месте без форка.

Ключевые классы плагина (`packages/android_automotive_plugin/lib/`):
- `AndroidAutomotivePlugin` — методы `connect()`, `getHvacIntProperty()`, `setHvacIntProperty()`, колбэк `onHvacChangeEventCallback`, sensor callbacks. **Важно**: конструктор регистрирует `MethodCallHandler` на канале `android_automotive_plugin` (имя `const`), поэтому повторное создание `new AndroidAutomotivePlugin()` перезатирает handler. Получать инстанс только через `locator<HvacService>().androidAutomotivePlugin`.
- `car/hvac_manager.dart` (`CarHvacManager`) — `setSeatHeatLevel(isDriver, level)`, `getInsideTemperature()`.
- `car/hvac_property_ids.dart` — `CarHvacPropertyIds.ID_HVAC_IN_OUT_TEMP`.
- `car/vehicle_area_in_out_car.dart` — `VehicleAreaInOutCAR.InOutCAR_INSIDE`.
- `car/sensor_manager.dart`, `car_sensor_event.dart`, `car_sensor_types.dart`, `ignition_state.dart` — работа с датчиками и зажиганием (используется в `background_runtime_controller.handleIgnition` для shutdown сидений).

При расширении функционала **сначала проверять, есть ли уже метод в плагине**, и только потом дополнять плагин (а не дублировать вызовы `setHvacIntProperty` в коде приложения).

## Архитектурные потоки

Подробно — в `ARCHITECTURE.md`. Краткие инварианты:

1. **Установка уровня подогрева**: UI → `ModeCubit.setHeatLevel()` → `ModeService` (persistence) + `HvacService.setSeatHeatLevel()` → `CarHvacManager` → нативный слой.
2. **Автоматический режим**: `HvacService` подписан на `onHvacChangeEvent`. При изменении `ID_HVAC_IN_OUT_TEMP` для `InOutCAR_INSIDE` температура публикуется через multi-listener API в `AutoHeatService` и `CabinTemperatureCubit`. `AutoHeatService` запускает каскад `3 → 2 → 1 → 0` по таймерам из `TemperatureConstants.temperatureSequences` с **адаптивным step-down по температуре** и **plan-key guard** (после завершения каскада повторное событие с той же температурой не перезапускает уровень 3 — только смена `HeatSequence` рестартует).
3. **Режим пресетов**: applyPreset → `AutoHeatService.startAutoHeat(settings: preset.settings)`. На старте — гейт по `settings.temperatureThreshold`. После старта каскад фиксированной длительности, температурные события игнорируются (`isPresetRunning` early-return).
4. **Преобразование температуры**: датчик возвращает int; формула `(value - 84) / 2 = °C` (см. `HvacService._convertToCelsius`). Все слайдеры/пороги UI работают в Цельсиях.
5. **Background**: `flutter_background_service` поднимает foreground-service. В `onStart` создаётся **отдельный** `AndroidAutomotivePlugin` через `setupServiceLocator()` (фон работает в отдельном изоляте — состояние UI-изолята недоступно). Используется `DartPluginRegistrant.ensureInitialized()` и `@pragma('vm:entry-point')`. Connect идёт через `HvacService.initialize()` с in-flight guard, чтобы параллельные пути не делали двойной `plugin.connect()`.
6. **Ignition OFF → shutdown**: `BackgroundRuntimeController.handleIgnition` подписан на sensor callback и при `IGNITION_STATE_ON == false` последовательно вызывает `setHeatLevel(driver, 0)` + `setHeatLevel(passenger, 0)`.
7. **Accessibility-колбэк**: `setCallbackHandle` поднимает background-service при системных событиях (точка входа `_accessibilityServiceCallback`, тоже `@pragma('vm:entry-point')`). **Важно**: вызывается на `locator<HvacService>().androidAutomotivePlugin`, а не на новом instance.

## Debug-режим

Скрытый toggle для тестирования авто-режима без реальных sensor-событий (например, летом или на эмуляторе):

- **Активация**: длительный тап по индикатору «Температура в салоне». Persist в `SharedPreferences['debug_mode']`.
- **Включён** → в шапке появляется четвёртая вкладка «**Логи**» с двумя секциями:
  - **Слева** — живой просмотр `LogRingBuffer.instance` (in-memory кольцевой буфер на 500 строк, заполняется параллельно с `print`-выводом через `Logger._write`). Auto-scroll, Copy-all, Clear; эвристическая подсветка `error/warn/fallback`.
  - **Справа (sidebar 320 px)** — инжектор температуры: chips `[-15, -10, -5, 0, 5, 10]°C` (две строки по три), произвольный TextField с decimal/signed валидацией. Оба пути вызывают `AutoHeatService().setTemperature(x)` — тот же путь, что и реальный sensor event.
- **Выключен** → таб скрывается, `HvacService.getCabinTemperature()` публикует реальную температуру всем listener'ам (вытесняя injected значение), `LogRingBuffer.instance.clear()` освобождает память.

В release-сборке debug-mode присутствует и доступен (по дизайну — для тестирования на голове без сборки debug-варианта).

## CI и релизы

`.github/workflows/release.yml` — Actions workflow, триггер на push тега `v*`:
1. Checkout с `fetch-depth: 0`, JDK 17 (Temurin), Flutter 3.41.9 stable.
2. `flutter pub get` (root + plugin), `flutter analyze`, `flutter test`.
3. `flutter build apk --release --target-platform android-arm64` → `build/app/outputs/apk/release/AutoHeat-v3.apk`.
4. AWK-парсер вырезает секцию `## [VERSION]` из `CHANGELOG.md` в `release_body.md` (fallback: `Release VERSION`).
5. `softprops/action-gh-release@v2` создаёт GitHub Release с APK как asset и `release_body.md` как описание. `fail_on_unmatched_files: true` — отсутствие APK завалит workflow.

**Перед каждым релизом**:
1. Бампнуть `version` в `pubspec.yaml` (например, `1.1.0`).
2. Добавить секцию `## [1.1.0] - YYYY-MM-DD` в `CHANGELOG.md` под `## [Unreleased]`.
3. `git tag v1.1.0 && git push origin v1.1.0`.

`permissions: contents: write` указано на уровне job — GITHUB_TOKEN сможет создать release. Проверить в репозитории: **Settings → Actions → General → Workflow permissions** не должно стоять «Read repository contents permission only».

## Правила работы с кодом

- **Никогда не вызывать `AndroidAutomotivePlugin` напрямую из UI / Cubit** — только через сервисы (`HvacService`).
- **Никогда не создавать `new AndroidAutomotivePlugin()`** вне `HvacService` — это перезаписывает `MethodCallHandler` канала. Доступ к instance — через `locator<HvacService>().androidAutomotivePlugin`.
- **Любой код в background-изоляте** должен быть помечен `@pragma('vm:entry-point')` и не полагаться на состояние, инициализированное в UI-изоляте.
- **`HvacService.initialize()`** идёт под in-flight guard — параллельные вызовы делят один `connect()`. Не пытаться обходить guard прямым `plugin.connect()`.
- **Ошибки от плагина** проглатывать с логированием и graceful fallback (так уже сделано в `HvacService.getCabinTemperature` → 20.0°C). Приложение крутится в авто — нельзя крашить процесс.
- **Persistence ключи и enum-имена** не переименовывать без миграции — пользовательские настройки лежат в `SharedPreferences` по строковым ключам, а `HeatMode`/`UserType` сериализуются через `.name`.
- **Кодогенерация**: после правок `@JsonSerializable` моделей запускать `dart run build_runner build --delete-conflicting-outputs`.
- **UI**: новые экраны/виджеты размещать строго в `lib/src/presentation/screens|ui` и использовать темы из `ThemeCubit`, а не хардкодить цвета.
- **Локализация**: интерфейс и логи — на русском (например, `'Сервис подогрева сидений активен'`), сохранять этот стиль.
- **Логи**: использовать `Logger.info/warn/error/debug` с триплетом `module/function/block`, **не использовать прямой `print`**. Logger пишет и в print, и в `LogRingBuffer` (для debug UI).

## Команды

```bash
# Зависимости
flutter pub get
( cd packages/android_automotive_plugin && flutter pub get )

# Кодогенерация моделей
dart run build_runner build --delete-conflicting-outputs

# Запуск на подключённой голове / эмуляторе Android Automotive
flutter run

# Сборка APK для установки на head unit (только arm64-v8a, ~19 MB)
# Голова Changan UNI-S / CS55 Plus — arm64 (MediaTek MT8666 family, '_64_car' build).
# Flutter native libs контролируются ИМЕННО через --target-platform, abiFilters
# в build.gradle на libflutter.so/libapp.so не действует.
# Итоговый файл: build/app/outputs/apk/release/AutoHeat-v3.apk
# (имя задано через outputFileName в android/app/build.gradle).
# Параллельно Flutter кладёт свою копию в build/app/outputs/flutter-apk/app-release.apk
# — она нужна ему для flutter install/run, переименовывать не следует.
flutter build apk --release --target-platform android-arm64

# Анализ
flutter analyze

# Тесты
flutter test
# Журнал сценариев auto/preset с дампом Logger:
flutter test test/scenarios/walkthrough_log_test.dart --reporter expanded

# Релиз (создаётся автоматически через GitHub Actions)
git tag v1.0.0
git push origin v1.0.0
```

## Чего НЕ делать

- Не вводить адаптивную вёрстку под телефоны/планшеты, оставаться в рамках разрешения головного устройства.
- Не подменять `AndroidAutomotivePlugin` моками в production-коде; для отладки вне машины — fallback внутри сервиса (значение по умолчанию температуры `20.0`, см. `HvacService.getCabinTemperature`) и debug-инжектор.
- Не убирать foreground-service: на Android 13+ без него процесс будет убит, и автоподогрев перестанет работать при свёрнутом приложении.
- Не использовать прямой `print` для диагностических данных: писать через `Logger` (`lib/src/utils/logger.dart`), который централизованно форматирует `[Module][function][BLOCK]` и копирует строки в `LogRingBuffer` для debug UI.
- Не создавать второй `AndroidAutomotivePlugin` — `MethodChannel` с тем же именем единственен, второй конструктор перезатирает handler и UI-изолят теряет cabin-temp события.

## Полезные ссылки на код

- Точка входа: `lib/main.dart`
- DI: `lib/src/di/service_locator.dart`
- HVAC-обёртка: `lib/src/services/hvac_service.dart`
- Автоподогрев: `lib/src/services/auto_heat_service.dart`
- Фон: `lib/src/services/background_service.dart`, `lib/src/services/background_runtime_controller.dart`
- Логгер: `lib/src/utils/logger.dart`
- Температурные диапазоны и расписания: `lib/src/constants/temperature_constants.dart`
- Debug UI: `lib/src/presentation/screens/debug/logs_screen.dart`
- Локальный плагин: `packages/android_automotive_plugin/`
- Walkthrough-сценарии: `test/scenarios/walkthrough_log_test.dart`
- CI / релизы: `.github/workflows/release.yml`, `CHANGELOG.md`
- Архитектурная схема: `ARCHITECTURE.md`
</content>
</invoke>