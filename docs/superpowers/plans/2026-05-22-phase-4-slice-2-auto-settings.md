# Phase-4 Slice 2 Auto Settings Runtime Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Fix FA-002 so user-edited `ManualHeatSettings` duration/threshold values drive runtime auto heating.

**Architecture:** Keep `TemperatureConstants` as fallback when no user settings are supplied. Add optional per-`UserType` `ManualHeatSettings` support in `AutoHeatService`; `ModeCubit` loads settings from `ManualSettingsService` before starting auto mode and passes them into `AutoHeatService.startAutoHeat`. Runtime custom sequence semantics: if `currentTemperature >= settings.temperatureThreshold`, callback level `0`; otherwise run `3 -> 2 -> 1 -> 0` using durations from settings levels `3`, `2`, `1`.

**Tech Stack:** Flutter/Dart, `flutter_test`, `fake_async`, `shared_preferences`, existing `FakeHvacService`, `LoggerTestSink`, GRACE semantic markup.

---

## Scope decisions

1. `ManualHeatSettings.temperatureThreshold` means: auto heating is active only when cabin temperature is below threshold.
2. `ManualHeatSettings.autoHeatLevels` durations are interpreted by their `level` field, not by list index.
3. Existing `TemperatureConstants` behavior stays as fallback for callers/tests that do not provide settings.
4. `ModeCubit` constructor gains `ManualSettingsService`; DI and tests must be updated.
5. Remaining FA-003/FA-005 are out of scope: initial temperature read and same-range sensor restart guard remain documented.

---

## Task 1: Add custom settings behavior to AutoHeatService

**Files:**
- Modify: `test/unit/auto_heat_service_test.dart`
- Modify: `lib/src/services/auto_heat_service.dart`

- [x] **Step 1: Write failing tests**

Add imports to `test/unit/auto_heat_service_test.dart`:

```dart
import 'package:autoheat/src/models/manual_settings.dart';
```

Add before `START_BLOCK_HVAC_WIRING`:

```dart
  // START_BLOCK_CUSTOM_SETTINGS
  test('scenario-10: custom settings durations drive 3->2->1->0 schedule', () {
    fakeAsync((async) {
      final captured = <int>[];
      final settings = ManualHeatSettings(
        autoHeatLevels: const [
          AutoHeatLevel(level: 1, duration: 1),
          AutoHeatLevel(level: 2, duration: 2),
          AutoHeatLevel(level: 3, duration: 3),
        ],
        temperatureThreshold: 5.0,
      );

      AutoHeatService().setTemperature(4.0);
      AutoHeatService().startAutoHeat(UserType.driver, captured.add, settings: settings);

      expect(captured, [3]);
      async.elapse(const Duration(minutes: 3));
      expect(captured, [3, 2]);
      async.elapse(const Duration(minutes: 2));
      expect(captured, [3, 2, 1]);
      async.elapse(const Duration(minutes: 1));
      expect(captured, [3, 2, 1, 0]);
    });
  });

  test('scenario-11: custom threshold turns auto off at or above threshold', () {
    fakeAsync((async) {
      final captured = <int>[];
      final settings = ManualHeatSettings(
        autoHeatLevels: const [
          AutoHeatLevel(level: 1, duration: 1),
          AutoHeatLevel(level: 2, duration: 2),
          AutoHeatLevel(level: 3, duration: 3),
        ],
        temperatureThreshold: 5.0,
      );

      AutoHeatService().setTemperature(5.0);
      AutoHeatService().startAutoHeat(UserType.driver, captured.add, settings: settings);

      expect(captured, [0]);
      async.elapse(const Duration(minutes: 10));
      expect(captured, [0]);
    });
  });
  // END_BLOCK_CUSTOM_SETTINGS
```

Run:

```bash
flutter test test/unit/auto_heat_service_test.dart
```

Expected RED: `startAutoHeat` has no named `settings` parameter.

- [x] **Step 2: Implement minimal AutoHeatService settings support**

In `lib/src/services/auto_heat_service.dart`:
- import `package:autoheat/src/models/manual_settings.dart`; 
- add `final Map<UserType, ManualHeatSettings> _manualSettingsByUser = {};`
- change `startAutoHeat` signature to:

```dart
  void startAutoHeat(
    UserType userType,
    Function(int) onHeatLevelChanged, {
    ManualHeatSettings? settings,
  }) {
    _heatLevelCallbacks[userType] = onHeatLevelChanged;
    if (settings != null) {
      _manualSettingsByUser[userType] = settings;
    }
    _updateAutoHeatForUser(userType);
  }
```

- in `stopAutoHeat`, remove settings: `_manualSettingsByUser.remove(userType);`
- replace `_getSequence()` with `_getSequence(UserType userType)` and implement:

```dart
  HeatSequence? _getSequence(UserType userType) {
    if (_currentTemperature == null) return null;

    final settings = _manualSettingsByUser[userType];
    if (settings == null) {
      return TemperatureConstants.getHeatSequence(_currentTemperature!);
    }

    if (_currentTemperature! >= settings.temperatureThreshold) return null;

    int durationFor(int level) {
      for (final autoHeatLevel in settings.autoHeatLevels) {
        if (autoHeatLevel.level == level) {
          return autoHeatLevel.duration.clamp(0, 15);
        }
      }
      return 0;
    }

    return HeatSequence(
      level3Duration: durationFor(3),
      level2Duration: durationFor(2),
      level1Duration: durationFor(1),
    );
  }
```

- update `_updateAutoHeatForUser` to call `_getSequence(userType)`.
- update timer callbacks to call `_getSequence(userType)`.
- clear `_manualSettingsByUser` in `dispose()`.

Run:

```bash
flutter test test/unit/auto_heat_service_test.dart
flutter analyze lib/src/services/auto_heat_service.dart
```

Expected GREEN.

---

## Task 2: Wire ModeCubit to ManualSettingsService

**Files:**
- Modify: `test/unit/mode_cubit_test.dart`
- Modify: `lib/src/cubit/mode_cubit.dart`
- Modify: `lib/src/di/service_locator.dart`

- [x] **Step 1: Write failing ModeCubit regression test**

In `test/unit/mode_cubit_test.dart`:
- import `dart:convert`; 
- import `package:autoheat/src/services/manual_settings_service.dart`; 
- update `buildCubit` to create `ManualSettingsService(prefs)` and pass it to `ModeCubit`.

Add before `START_BLOCK_AUTO_END_TO_END`:

```dart
  test('scenario-12: auto mode uses persisted ManualSettings threshold', () async {
    final customSettings = ManualHeatSettings(
      autoHeatLevels: const [
        AutoHeatLevel(level: 1, duration: 1),
        AutoHeatLevel(level: 2, duration: 2),
        AutoHeatLevel(level: 3, duration: 3),
      ],
      temperatureThreshold: -10.0,
    );
    final (cubit, fakeHvac, prefs) = await buildCubit({});
    await prefs.setString('manual_settings_driver', json.encode(customSettings.toJson()));

    await cubit.setMode(UserType.driver, HeatMode.auto.name);
    fakeHvac.emitTemperature(-3.0);
    await pumpEventQueue();

    expect(stateOf(cubit, UserType.driver).heatLevel, 0);
    expect(fakeHvac.recordedSetSeatHeatCalls, contains((userType: UserType.driver, level: 0)));
  });
```

Run:

```bash
flutter test test/unit/mode_cubit_test.dart
```

Expected RED after constructor/test update until implementation is complete.

- [x] **Step 2: Implement ModeCubit dependency and async auto start**

In `lib/src/cubit/mode_cubit.dart`:
- import `package:autoheat/src/services/manual_settings_service.dart`; 
- add field `final ManualSettingsService _manualSettingsService;`
- update constructor: `ModeCubit(this._modeService, this._hvacService, this._manualSettingsService)`.
- add helper:

```dart
  Future<void> _startAutoHeat(UserType userType) async {
    final settings = await _manualSettingsService.getSettings(userType);
    _autoHeatService.startAutoHeat(
      userType,
      (newLevel) {
        setHeatLevel(userType, newLevel);
      },
      settings: settings,
    );
  }
```

- replace `_manageAutoHeat` with async `Future<void> _manageAutoHeat(...)` that awaits `_startAutoHeat` for auto, otherwise stops.
- in `setMode` and `applyPreset`, `await _manageAutoHeat(...)`.
- in `_initializeHeatModes`, make it `Future<void>` and await `_startAutoHeat`; in `_initialize`, `await _initializeHeatModes(states);`.

In `lib/src/di/service_locator.dart`, update registration:

```dart
  locator.registerSingleton<ModeCubit>(
    ModeCubit(
      locator<ModeService>(),
      locator<HvacService>(),
      locator<ManualSettingsService>(),
    ),
  );
```

Run:

```bash
flutter test test/unit/mode_cubit_test.dart
flutter analyze lib/src/cubit/mode_cubit.dart lib/src/di/service_locator.dart
```

Expected GREEN.

---

## Task 3: Update docs and verification

**Files:**
- Modify: `docs/development-plan.xml`
- Modify: `docs/verification-plan.xml`
- Modify: `docs/functional-audit-findings.md`
- Modify: `docs/knowledge-graph.xml`

- [x] Update Phase-4 step-4 to `status="done"` and mention `ManualSettingsService -> ModeCubit -> AutoHeatService`.
- [x] In `V-M-AUTO-HEAT`, add scenarios for custom settings durations and threshold.
- [x] In `V-M-MODE`, add scenario for persisted manual settings threshold used by auto mode.
- [x] Mark FA-002 addressed in `docs/functional-audit-findings.md`.
- [x] Update `M-MODE` dependency docs to include `M-MANUAL-SETTINGS` and `M-AUTO-HEAT` docs to mention optional manual settings.

Run:

```bash
grace lint --profile standard
```

Expected: no issues.

---

## Task 4: Full verification and one commit

Run:

```bash
flutter test
flutter analyze
grace lint --profile standard
git diff --check
```

Expected: all pass.

Commit once:

```bash
git add .
git commit -m "Use manual settings for auto heat"
```

---

## Self-review

- FA-002 is covered directly by AutoHeatService custom settings tests and ModeCubit persisted settings test.
- No SDK/dependency change is required.
- `TemperatureConstants` fallback remains unchanged, preserving existing behavior and tests.
- Remaining FA-003/FA-005 are intentionally out of scope.
