# Functional Audit Findings — autoheat

Дата: 2026-05-22  
Контекст: ручной проход по runtime/UI-логике после Phase-3 structured logging.  
Scope: `lib/main.dart`, DI, services/cubits, heat/settings/presets UI, локальный `packages/android_automotive_plugin`.  
Не является результатом manual smoke на head unit; это статический logic/UI audit по коду.

## Summary

Автоматические gate-команды после Phase-3 зелёные, но ручной проход по пользовательским потокам выявил несколько расхождений между требованиями/DF-* и фактическим кодом. Главный риск: часть UI визуально отрабатывает, но не доходит до `ModeCubit`/`HvacService`, либо настройки сохраняются, но не участвуют в реальном алгоритме.

## Findings

### FA-001 — Пресеты не применяют реальные режимы/уровни к HVAC

Status: addressed in Phase-4 Slice-1 by routing preset apply through `ModeCubit.applyPreset` to `HvacService.setSeatHeatLevel`.
Priority: critical
Modules: `M-PRESET`, `M-MODE`, `M-HVAC`, `M-UI-PRESETS`  
Related: `UC-003`, `DF-PRESET-APPLY`, `VF-004`

Evidence:
- `lib/src/presentation/app_content.dart` `_applyPreset(preset)` вызывает только:
  - `ManualSettingsCubit.applyPresetSettings(...)`
  - `PresetCubit.applyPreset(preset)`
- `ModeCubit.setMode(...)`, `ModeCubit.setHeatLevel(...)` и `HvacService.setSeatHeatLevel(...)` не вызываются.
- `docs/development-plan.xml` `DF-PRESET-APPLY` требует: `PresetCubit.apply(preset)` → `ModeCubit` → `HvacService`.

Observed consequence:
- Пользователь видит snackbar «Пресет применен», manual-settings состояние обновляется, но физический подогрев не меняется.
- `HeatMode.presets` в `ModeCubit` хранится как enum, но не имеет отдельной runtime-логики применения.

Suggested fix direction:
- Уточнить контракт пресета: пресет хранит только `ManualHeatSettings` одного сиденья или должен хранить режим+уровни для применения к `ModeCubit`.
- В `AppContent._applyPreset` или в `PresetCubit.applyPreset` прокинуть действие в `ModeCubit`:
  - выставить режим `presets` или `manual` согласно выбранному контракту;
  - вызвать `setHeatLevel(userType, targetLevel)` / расписание применения;
  - добавить marker assertions для `DF-PRESET-APPLY`.

---

### FA-002 — Настройки автоподогрева сохраняются, но не влияют на `AutoHeatService`

Priority: critical  
Modules: `M-MANUAL-SETTINGS`, `M-AUTO-HEAT`, `M-CONSTANTS-TEMPERATURE`, `M-UI-SETTINGS`  
Related: `UC-002`, `UC-003`, `DF-AUTO-HEAT`

Evidence:
- `lib/src/presentation/screens/settings/components/*` позволяют менять:
  - `AutoHeatLevel.duration`
  - `ManualHeatSettings.temperatureThreshold`
- `ManualSettingsCubit` сохраняет эти значения через `ManualSettingsService`.
- `lib/src/services/auto_heat_service.dart` использует только `TemperatureConstants.getHeatSequence(_currentTemperature!)`.
- `ManualSettingsCubit`/`ManualSettingsService` никак не инжектируются в `AutoHeatService` или `ModeCubit` для расчёта runtime-расписания.

Observed consequence:
- Пользователь двигает слайдеры, значения сохраняются, но реальное авто-расписание остаётся статическим из `TemperatureConstants`.
- Порог «Включать, когда температура ниже X°C» не используется в `AutoHeatService`.

Suggested fix direction:
- Решить, кто владеет пользовательским расписанием: `ManualSettingsCubit`, новый domain service или расширенный `AutoHeatService`.
- Передавать per-`UserType` settings в `AutoHeatService.startAutoHeat(...)` или дать `AutoHeatService` read-only dependency на settings-service.
- Добавить deterministic tests: изменение duration/threshold меняет уровни/таймеры.

---

### FA-003 — Температура салона в UI обновляется ненадёжно и нет initial read

Priority: high  
Modules: `M-AUTO-HEAT`, `M-MODE`, `M-HVAC`, `M-UI-HEAT`  
Related: `DF-INIT-TEMP`, `DF-AUTO-HEAT`

Evidence:
- `CabinTemperatureDisplay` читает `context.read<ModeCubit>().cabinTemperature` внутри `BlocBuilder<ModeCubit, ModesState>`.
- `AutoHeatService.initialize(...)` обновляет `_currentTemperature`, но не имеет собственного stream/state и не заставляет `ModeCubit` emit при каждом temperature event.
- Если активных auto callbacks нет, `_updateAutoHeatForAllUsers()` не вызывает `ModeCubit.setHeatLevel(...)`, следовательно `ModesState` не меняется и UI может не перерисоваться.
- `HvacService.getCabinTemperature()` существует, но при старте auto-логики/экрана нигде не вызывается.

Observed consequence:
- В manual/presets режиме температура может оставаться `-- °C` или устаревшей, пока не произойдёт другой state-change.
- При включении auto до первого HVAC event расписание не стартует, потому что `_currentTemperature == null`.

Suggested fix direction:
- Ввести явный state/stream температуры (`ModeCubit` field + emit on temperature event, отдельный cubit, или listener-модель в `HvacService`).
- При инициализации вызвать `HvacService.getCabinTemperature()` и засидить initial temperature.
- Добавить UI/unit checks на обновление температуры без изменения heat level.

---

### FA-004 — Переключение в manual визуально сбрасывает уровень, но не отправляет HVAC=0

Status: addressed in Phase-4 Slice-1 for `ModeCubit.setMode(... manual)` when current heatLevel > 0.
Priority: high
Modules: `M-MODE`, `M-HVAC`, `M-UI-HEAT`  
Related: `UC-001`, `DF-SET-HEAT`

Evidence:
- `ModeCubit.setMode(...)`:
  - для `HeatMode.manual` вычисляет `newHeatLevel = 0`;
  - вызывает `_updateUserState(...)`;
  - вызывает `_manageAutoHeat(...)`, где `stopAutoHeat(userType)` только отменяет таймер/callback.
- `setHeatLevel(userType, 0)` при этом не вызывается.

Observed consequence:
- UI показывает manual + level 0, но физический подогрев может остаться на предыдущем уровне.
- Особенно рискованно при переходе `auto -> manual`, когда timer уже выставил уровень 3/2/1.

Suggested fix direction:
- При переходе в manual с forced level 0 явно вызывать `await setHeatLevel(userType, 0)` или менять контракт: manual сохраняет текущий уровень без сброса.
- Развести «сменить режим» и «применить уровень» так, чтобы UI state не опережал HVAC state.

---

### FA-005 — Частые события температуры могут бесконечно перезапускать auto sequence

Priority: high  
Modules: `M-AUTO-HEAT`, `M-HVAC`  
Related: `UC-002`, `DF-AUTO-HEAT`

Evidence:
- `AutoHeatService._updateAutoHeatForUser(...)` на каждый temperature event:
  - отменяет `_heatTimers[userType]`;
  - заново вызывает `_startHeatSequence(...)`;
  - callback сразу ставит level 3.
- Нет hysteresis/debounce/range-change guard.

Observed consequence:
- Если HVAC датчик шумит или часто эмитит одинаковый диапазон, сиденье может постоянно возвращаться на уровень 3 и никогда не дойти до 2/1/0.

Suggested fix direction:
- Хранить последний `TemperatureRange`/sequence per user и перезапускать только при изменении диапазона или явном mode transition.
- Добавить debounce/throttle либо hysteresis around thresholds.
- Добавить FakeAsync тест: repeated same-range temperature events не рестартят sequence.

---

### FA-006 — Background service stop/restart lifecycle неполный

Priority: high  
Modules: `M-BACKGROUND`, `M-DI`, `M-PLUGIN`  
Related: `UC-004`, `DF-BACKGROUND`, `V-M-BACKGROUND`

Evidence:
- `stopBackgroundService()` вызывает `service.invoke('stopService')`.
- В `onStart(ServiceInstance service)` нет подписки `service.on('stopService')` и нет обработчика, который вызывает `stopSelf()`.
- Restart-backoff в catch меняет notification через `Timer`, но не вызывает повторный `connect()`/перезапуск сервиса.
- Ignition OFF вызывает `_modeCubit.setHeatLevel(..., 0)` без `await`, ошибки могут потеряться.

Observed consequence:
- Вызов stop может не останавливать foreground-service.
- При ошибке старта может появиться «Перезапуск сервиса...», но реальный reconnect не произойдёт.
- Ignition OFF может завершиться до применения HVAC=0.

Suggested fix direction:
- Добавить `service.on('stopService').listen(...)` с `stopSelf()` для Android.
- Сформулировать retry contract: reconnect plugin, restart service, или fail-stop после max attempts.
- Await/sequence ignition OFF heat-level calls и логировать failure per seat.
- Проверять только manual smoke на голове или выделить абстракцию для unit harness.

---

### FA-007 — `AndroidAutomotivePlugin.connect/setHvac*Property` fire-and-forget

Priority: high / known finding  
Modules: `M-PLUGIN`, `M-HVAC`  
Related: existing `F-1` in `docs/verification-plan.xml`

Evidence:
- `packages/android_automotive_plugin/lib/android_automotive_plugin.dart`:
  - `connect()` вызывает `methodChannel.invokeMethod("connect")` без `await`/`return`.
  - `setHvacIntProperty(...)` и `setHvacFloatProperty(...)` аналогично.
- `HvacService.initialize()` и `setSeatHeatLevel()` имеют `try/catch`, но write/connect ошибки из plugin не доходят.

Observed consequence:
- UI и сервисы считают операцию успешной, даже если native write/connect упали.

Suggested fix direction:
- Либо исправить plugin methods на `await/return`, либо явно оставить как snapshot limitation и добавить read-back verification в `HvacService`.
- Так как это уже зафиксировано как deferred-4/F-1, не смешивать с preset/settings fixes без отдельного решения.

---

### FA-008 — DI setup неидемпотентен внутри одного isolate

Priority: medium  
Modules: `M-DI`, `M-MAIN`, `M-BACKGROUND`

Evidence:
- `setupServiceLocator()` всегда вызывает `locator.registerSingleton<T>(...)` без reset/isRegistered guards.
- В нормальном UI/background isolate это вызывается один раз, но повторный вызов в том же isolate бросит GetIt registration error.

Observed consequence:
- Hot restart, tests, manual retry или повторный bootstrap в одном isolate могут падать из-за duplicate registration.

Suggested fix direction:
- Сделать `setupServiceLocator()` idempotent (`isRegistered` guards) или документировать/проверять reset before setup в тестах.
- При изменении обязательно проверить и UI-, и background-onStart-путь (project rule).

---

### FA-009 — Settings layout: `Expanded` inside scrollable/intrinsic tree может упасть при build

Priority: medium  
Modules: `M-UI-SETTINGS`

Evidence:
- `SettingsScreen` возвращает `SingleChildScrollView`.
- `PresetsSettings` строит `IntrinsicHeight -> Row -> Expanded -> Padding -> Column -> Expanded(child: ManualSettingsSection(...))`.
- `Expanded` внутри `Column` с неограниченной высотой — частый Flutter runtime error (`RenderFlex children have non-zero flex but incoming height constraints are unbounded`).

Observed consequence:
- Экран настроек может падать или некорректно layout-иться на head unit, особенно при изменении размеров/текста.

Suggested fix direction:
- Убрать внутренние `Expanded` вокруг `ManualSettingsSection`, заменить на обычный child/`Flexible(fit: FlexFit.loose)` после проверки constraints.
- Сделать widget smoke/golden-ish test для `SettingsScreen` с fixed head-unit size.

---

### FA-010 — `ModeCubit.toggleHeatLevel` при выходе из non-manual запускает async mode и level параллельно

Status: addressed in Phase-4 Slice-1 by making `toggleHeatLevel` async and sequencing non-manual → manual level 1.
Priority: medium
Modules: `M-MODE`, `M-UI-HEAT`

Evidence:
- `toggleHeatLevel(...)` в ветке non-manual:
  - `setMode(userType, HeatMode.manual.name);`
  - `setHeatLevel(userType, 1);`
- Оба Future не await-ятся и запускаются подряд.

Observed consequence:
- Возможна гонка записи prefs/state/HVAC при тапе по сиденью из auto/presets режима.
- UI может временно получить mode/level в неожиданном порядке.

Suggested fix direction:
- Сделать `toggleHeatLevel` async и await последовательность.
- Решить ожидаемый UX: тап по сиденью из auto должен переключать manual и уровень 1 или просто игнорироваться/показывать подсказку.

---

### FA-011 — `HeatMode.presets` есть в UI, но нет полноценной state-machine семантики

Status: partially addressed in Phase-4 Slice-1: presets now carry/apply runtime `heatMode`/`heatLevel`; selected preset-id persistence remains out of scope.
Priority: medium
Modules: `M-MODE`, `M-PRESET`, `M-UI-HEAT`, `M-UI-PRESETS`

Evidence:
- `ModeToggler` позволяет выбрать `presets`.
- `ModeCubit._manageAutoHeat(...)` для любого non-auto вызывает `stopAutoHeat`.
- Нет логики, связывающей `HeatMode.presets` с выбранным `Preset` или уровнем.

Observed consequence:
- Режим `presets` выглядит как отдельный режим, но фактически ведёт себя почти как manual без применения preset.

Suggested fix direction:
- Либо убрать `presets` из segmented control и оставить пресеты как action из отдельной вкладки.
- Либо определить state-machine: selected preset per user, apply mode, persisted preset id, fallback на manual.

---

### FA-012 — `ManualSettingsState.copyWith` не может очистить error

Priority: low  
Modules: `M-MANUAL-SETTINGS`

Evidence:
- `ManualSettingsState.copyWith({ String? error })` использует `error: error ?? this.error`.
- После ошибки последующий успешный emit без `error: null` не очищает error.

Observed consequence:
- `PresetsSection` может продолжать показывать `ErrorBlock` после transient ошибки, пока cubit не пересоздан.

Suggested fix direction:
- Добавить `clearError` флаг как в `PresetState.copyWith`, либо всегда очищать error на успешных operations.

---

## Suggested next phase

Рекомендуемая следующая работа: `Phase-4 Functional hardening`.

Suggested ordering:
1. `FA-001` + `FA-011`: определить и починить semantics preset mode/apply до `ModeCubit/HvacService`.
2. `FA-002`: подключить manual settings к auto algorithm.
3. `FA-003` + `FA-004` + `FA-010`: стабилизировать `ModeCubit` state machine, initial temperature и mode transitions.
4. `FA-005`: защитить auto sequence от sensor noise.
5. `FA-006`: отдельно hardened background lifecycle + head-unit smoke.
6. `FA-009`/`FA-012`: UI/state cleanup.

## Verification gaps to add before fixing

- Widget smoke for `SettingsScreen` at target head-unit size.
- Unit/integration test for `DF-PRESET-APPLY`: tap/apply preset causes `ModeCubit.setHeatLevel` and `HvacService.setSeatHeatLevel`.
- FakeAsync test: repeated same-range temperature events do not restart sequence.
- Temperature display test: HVAC temperature event updates visible UI even when no auto mode is active.
- Background service manual smoke checklist for stop/restart/ignition OFF.
