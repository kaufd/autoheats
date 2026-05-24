# AutoHeat

Flutter-приложение для автоматического управления подогревом сидений в автомобиле Changan через головное устройство на Android Automotive OS.

## Контекст и назначение

- **Платформа выполнения**: головное устройство (head unit) автомобиля Changan на базе Android Automotive.
- **Происхождение**: переработка предыдущего проекта `/Users/kaufd/_Projects/open-source/changan/autoheat_old` (там плагин подключался как git-зависимость, в нынешнем проекте — переехал в `packages/` как path-зависимость).
- **Назначение приложения**: вместо ручного нажатия кнопок подогрева сидений приложение делает это автоматически на основе температуры в салоне и заданных пресетов, либо по расписанию мощности (3 → 2 → 1 → off).
- **Режимы работы**:
  - `manual` — ручное управление уровнем подогрева;
  - `presets` — заранее сохранённые пользовательские конфигурации;
  - `auto` — автоматический режим по датчику температуры салона.

## Аппаратные и платформенные требования

- **OS**: Android Automotive (через `android.car.*` API), минимально `minSdk = 23`. Целевая модель — Changan; конкретные модели у разработчика не тестировались.
- **Экран**: фиксированное альбомное разрешение головного устройства. UI рассчитан на горизонтальную ориентацию и не должен «плыть» в произвольных размерах — при правках компонентов учитывать `MediaQuery` и проверять на разрешении head unit. Не закладывать поддержку телефонов/планшетов.
- **Системные привилегии**: для доступа к свойствам автомобиля (HVAC, ignition, sensors) приложение должно быть установлено как системное / иметь подписанный платформенным ключом APK на конкретной голове. Без этого `AndroidAutomotivePlugin.connect()` будет падать.
- **Разрешения** (см. `android/app/src/main/AndroidManifest.xml`):
  - `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` — фоновый сервис подогрева;
  - `POST_NOTIFICATIONS` — нотификация foreground-service;
  - `WAKE_LOCK` — удержание процесса активным.
- **applicationId / namespace**: `ru.kaufd.autoheat`.

## Технологический стек

- **Flutter** SDK `^3.6.0`, Dart 3.
- **State management**: `flutter_bloc` (Cubit) + `equatable`.
- **DI**: `get_it` (см. `lib/src/di/service_locator.dart`).
- **Persistence**: `shared_preferences` для всех пользовательских настроек, пресетов, режимов, выбранной темы и порога автоподогрева.
- **Фон**: `flutter_background_service` (foreground-service с уведомлением `my_foreground`, id `888`).
- **Кодогенерация**: `json_serializable` + `build_runner` (`*.g.dart` для моделей).
- **Линтер**: `flutter_lints`.

## Структура каталогов

```
lib/
  main.dart                    — точка входа, поднимает DI, темы, accessibility и background service
  src/
    app_enums.dart             — HeatMode, UserType + расширения
    cubit/                     — ModeCubit, ModeStateCubit, PresetCubit, SettingsCubit, ManualSettingsCubit
    di/                        — service_locator.dart (GetIt), app_bloc_providers.dart
    config/                    — темы, цвета, текстовые стили
    constants/                 — TemperatureConstants и HeatSequence (расписания подогрева по диапазонам температур)
    models/                    — Preset, ManualSettings (+ codegen *.g.dart)
    services/
      hvac_service.dart        — обёртка над AndroidAutomotivePlugin + CarHvacManager
      auto_heat_service.dart   — синглтон, реализует автоматический режим по температуре
      background_service.dart  — foreground-service, продолжает работу при свёрнутом приложении
      accessibility_service.dart
      mode_service.dart, preset_service.dart, settings_service.dart, manual_settings_service.dart
    extensions/
    presentation/
      app_content.dart
      themes/                  — ThemeCubit, ThemeService, ThemeConfigurator
      ui/                      — переиспользуемые виджеты (CustomSwitch, AlertDialog, ErrorBlock)
      screens/
        heat/                  — главный экран управления подогревом (Seat, CabinTemperatureDisplay, ModeToggler)
        presets/               — список пресетов
        settings/              — настройки темы, порога автоподогрева, пресетов, ручных настроек

packages/
  android_automotive_plugin/   — локальный плагин-обёртка над android.car.* API (по path-зависимости)
```

## Базовая библиотека

Проект использует локальный path-плагин `packages/android_automotive_plugin/`. Исходник — снапшот апстрима с GitHub: `https://github.com/abuharsky/changan_car_flutter_library` (namespace `com.bukharskii.flutter.automotive.android_automotive_plugin` — авторства abuharsky). Локальный слепок добавлен 2025-02-22 коммитом `6f4babc Add android_automotive_plugin as local package` и с тех пор не правился; апстрим тоже стагнирует (последний коммит 2025-01-13, активности нет уже больше года). Это не форк и не доработка — содержимое плагина полностью соответствует upstream на момент снапшота.

**Почему path, а не git-зависимость** (в `autoheat_old` плагин подключался через `git: url: ...`): upstream фактически заброшен, поэтому детерминированный локальный снапшот защищает сборку от исчезновения/архивации репозитория и работает офлайн. Если когда-нибудь потребуется правка под autoheat — её удобно делать на месте без форка. Регламента периодической синхронизации с апстримом нет: при появлении заметной активности у abuharsky или при необходимости конкретного фикса — обновлять вручную одним коммитом.

Никаких реальных тестов плагина на конкретной модели Changan со стороны разработчика autoheat не проводилось — метки «tested on …» в README плагина относятся к репозиторию автора и не должны рассматриваться как утверждение об autoheat.

Ключевые классы плагина (`packages/android_automotive_plugin/lib/`):
- `AndroidAutomotivePlugin` — методы `connect()`, `getHvacIntProperty()`, `setHvacIntProperty()`, колбэк `onHvacChangeEventCallback`, sensor callbacks.
- `car/hvac_manager.dart` (`CarHvacManager`) — высокоуровневые методы: `setSeatHeatLevel(isDriver, level)`, `getInsideTemperature()` и т.д.
- `car/hvac_property_ids.dart` — `CarHvacPropertyIds.ID_HVAC_IN_OUT_TEMP` и т.п.
- `car/vehicle_area_in_out_car.dart` — `VehicleAreaInOutCAR.InOutCAR_INSIDE`.
- `car/sensor_manager.dart`, `car_sensor_event.dart`, `car_sensor_types.dart`, `ignition_state.dart` — работа с датчиками и зажиганием (используется в `background_service.dart` для запуска/остановки сервиса по ignition).

При расширении функционала **сначала проверять, есть ли уже метод в плагине**, и только потом дополнять плагин (а не дублировать вызовы `setHvacIntProperty` в коде приложения).

## Архитектурные потоки

Подробно — в `ARCHITECTURE.md`. Краткие инварианты:

1. **Установка уровня подогрева**: UI → `ModeCubit.setHeatLevel()` → `ModeService` (persistence) + `HvacService.setSeatHeatLevel()` → `CarHvacManager` → нативный слой.
2. **Автоматический режим**: `HvacService` подписан на `onHvacChangeEvent`. При изменении `ID_HVAC_IN_OUT_TEMP` для `InOutCAR_INSIDE` вызывается `onCabinTemperatureChanged`, который `AutoHeatService` использует, чтобы запустить расписание из `TemperatureConstants.temperatureSequences` (3 → 2 → 1 → 0 по таймерам). Расписания зависят от диапазона температуры (см. `TemperatureRange`).
3. **Преобразование температуры**: датчик возвращает int; формула `(value - 84) / 2 = °C` (см. `HvacService._convertToCelsius`). Все слайдеры/пороги UI работают в Цельсиях.
4. **Background**: `flutter_background_service` поднимает foreground-service, в `onStart` создаётся **отдельный** `AndroidAutomotivePlugin` и инициализируется DI через `setupServiceLocator()` (фон работает в отдельном изоляте — состояние UI-изолята недоступно). Для входа в изолят используется `DartPluginRegistrant.ensureInitialized()` и `@pragma('vm:entry-point')`.
5. **Accessibility-колбэк**: `setCallbackHandle` поднимает background-service при системных событиях (точка входа `_accessibilityServiceCallback`, тоже `@pragma('vm:entry-point')`).

## Правила работы с кодом

- **Никогда не вызывать `AndroidAutomotivePlugin` напрямую из UI / Cubit** — только через сервисы (`HvacService`, расширять при необходимости).
- **Любой код в background-изоляте** должен быть помечен `@pragma('vm:entry-point')` и не полагаться на состояние, инициализированное в UI-изоляте.
- **Ошибки от плагина** проглатывать с логированием и graceful fallback (так уже сделано в `HvacService`). Приложение крутится в авто — нельзя крашить процесс.
- **Persistence ключи и enum-имена** не переименовывать без миграции — пользовательские настройки лежат в `SharedPreferences` по строковым ключам, а `HeatMode`/`UserType` сериализуются через `.name`.
- **Кодогенерация**: после правок `@JsonSerializable` моделей запускать `dart run build_runner build --delete-conflicting-outputs`.
- **UI**: новые экраны/виджеты размещать строго в `lib/src/presentation/screens|ui` и использовать темы из `ThemeCubit`, а не хардкодить цвета.
- **Локализация**: интерфейс и логи — на русском (например, `'Сервис подогрева сидений активен'`), сохранять этот стиль.

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
```

## Чего НЕ делать

- Не вводить адаптивную вёрстку под телефоны/планшеты, оставаться в рамках разрешения головного устройства.
- Не подменять `AndroidAutomotivePlugin` моками в production-коде; для отладки вне машины — fallback внутри сервиса (значение по умолчанию температуры `20.0`, см. `HvacService.getCabinTemperature`).
- Не убирать foreground-service: на Android 13+ без него процесс будет убит, и автоподогрев перестанет работать при свёрнутом приложении.
- Не использовать прямой `print` для диагностических данных: после Phase-3 писать через `Logger` (`lib/src/utils/logger.dart`), который централизованно форматирует `[Module][function][BLOCK]` и сам использует `print` как sink.

## Полезные ссылки на код

- Точка входа: `lib/main.dart`
- DI: `lib/src/di/service_locator.dart`
- HVAC-обёртка: `lib/src/services/hvac_service.dart`
- Автоподогрев: `lib/src/services/auto_heat_service.dart`
- Фон: `lib/src/services/background_service.dart`
- Температурные диапазоны и расписания: `lib/src/constants/temperature_constants.dart`
- Локальный плагин: `packages/android_automotive_plugin/`
- Архитектурная схема: `ARCHITECTURE.md`
