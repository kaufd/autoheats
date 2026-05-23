# Phase-4 Slice 3 Cabin Temperature Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Fix FA-003 by introducing a multi-listener HVAC temperature source and a dedicated CabinTemperatureCubit for UI temperature state.

**Architecture:** `HvacService` becomes the cached/multi-listener source of cabin temperature. `AutoHeatService` and `CabinTemperatureCubit` subscribe independently; `ModeCubit` seeds AutoHeatService from the initial HVAC read but does not expose UI temperature state.

**Tech Stack:** Flutter/Dart, flutter_bloc, flutter_test, fake_async, SharedPreferences mock, existing FakeHvacService and LoggerTestSink.

---

## Files

- Modify: `lib/src/services/hvac_service.dart`
- Modify: `test/_helpers/fake_hvac_service.dart`
- Modify: `test/unit/hvac_service_test.dart`
- Modify: `lib/src/services/auto_heat_service.dart`
- Modify: `test/unit/auto_heat_service_test.dart`
- Create: `lib/src/cubit/cabin_temperature_cubit.dart`
- Create: `test/unit/cabin_temperature_cubit_test.dart`
- Modify: `lib/src/cubit/mode_cubit.dart`
- Modify: `test/unit/mode_cubit_test.dart`
- Modify: `lib/src/di/service_locator.dart`
- Modify: `lib/src/di/app_bloc_providers.dart`
- Modify: `lib/src/presentation/screens/heat/components/cabin_temperature_display.dart`
- Create: `test/widget/cabin_temperature_display_test.dart`
- Modify: `docs/development-plan.xml`
- Modify: `docs/verification-plan.xml`
- Modify: `docs/knowledge-graph.xml`
- Modify: `docs/functional-audit-findings.md`

---

## Task 1: HvacService multi-listener contract

- [x] Write failing tests in `test/unit/hvac_service_test.dart`:
  - `scenario-10`: two listeners receive inside temperature events; removed listener stops receiving events.
  - `scenario-11`: `getCabinTemperature()` updates `lastCabinTemperature` and notifies listeners.
- [x] Run `flutter test test/unit/hvac_service_test.dart` and confirm RED because listener API is missing.
- [x] Implement in `lib/src/services/hvac_service.dart`:
  - `typedef CabinTemperatureListener = void Function(double celsius);`
  - `addCabinTemperatureListener(listener, {bool emitCurrent = false})`
  - `removeCabinTemperatureListener(listener)`
  - `double? get lastCabinTemperature`
  - `_publishCabinTemperature(double celsius)` that caches and notifies a copy of listeners.
  - listener errors are logged and do not stop other listeners.
  - sensor events and `getCabinTemperature()` call `_publishCabinTemperature`.
- [x] Update `test/_helpers/fake_hvac_service.dart` to implement the same API.
- [x] Convert existing HvacService tests from `onCabinTemperatureChanged` to `addCabinTemperatureListener`.
- [x] Run `flutter test test/unit/hvac_service_test.dart` and confirm GREEN.

## Task 2: AutoHeatService subscribes via listener API and supports initial seed

- [x] Write failing test in `test/unit/auto_heat_service_test.dart`:
  - initialize with `FakeHvacService.programmedTemperature = -3.0`, call `AutoHeatService().initialize(fakeHvac)`, call `seedCurrentTemperatureFromHvac()`, then `startAutoHeat(driver, capture)` and expect `[3]` without `emitTemperature`.
- [x] Run `flutter test test/unit/auto_heat_service_test.dart` and confirm RED because seed method/listener API is missing.
- [x] Implement in `lib/src/services/auto_heat_service.dart`:
  - store the registered `CabinTemperatureListener`.
  - `initialize` removes the old listener before adding a new listener with `emitCurrent: true`.
  - `Future<void> seedCurrentTemperatureFromHvac()` calls `_hvacService?.getCabinTemperature()` after subscription.
  - `dispose()` removes the listener, clears timers/callbacks/settings, and resets current temperature to `null`.
- [x] Update existing AutoHeatService tests to `await seedCurrentTemperatureFromHvac()` only where initial read is part of the scenario; event-driven tests keep using `emitTemperature`.
- [x] Run `flutter test test/unit/auto_heat_service_test.dart` and confirm GREEN.

## Task 3: CabinTemperatureCubit

- [x] Create failing `test/unit/cabin_temperature_cubit_test.dart` with module contract and tests:
  - initial read emits `programmedTemperature`.
  - cached `lastCabinTemperature` is emitted without a second read.
  - `emitTemperature` updates state in manual/no-auto context.
  - `close()` removes listener so later events do not change state.
- [x] Run `flutter test test/unit/cabin_temperature_cubit_test.dart` and confirm RED because cubit file is missing.
- [x] Create `lib/src/cubit/cabin_temperature_cubit.dart` with module contract:
  - `CabinTemperatureState extends Equatable` with `double? celsius` and `bool isLoading`.
  - `CabinTemperatureCubit(HvacService)` subscribes with `emitCurrent: true` and reads `getCabinTemperature()` if no cache exists.
  - `_onTemperatureChanged` emits `CabinTemperatureState(celsius: value, isLoading: false)`.
  - `close()` unsubscribes.
- [x] Run `flutter test test/unit/cabin_temperature_cubit_test.dart` and confirm GREEN.

## Task 4: Wire ModeCubit, DI, providers, and UI display

- [x] Write/update failing tests:
  - `test/unit/mode_cubit_test.dart`: add buildCubit option for `programmedTemperature`; auto mode starts from initial read without `emitTemperature`.
  - `test/widget/cabin_temperature_display_test.dart`: display shows initial temperature and updates after `FakeHvacService.emitTemperature`.
- [x] Run targeted tests and confirm RED while production wiring still uses old UI source.
- [x] Update `lib/src/cubit/mode_cubit.dart`:
  - remove `cabinTemperature` getter.
  - call `await _autoHeatService.seedCurrentTemperatureFromHvac()` after `initialize(_hvacService)` and before `_initializeHeatModes(states)`.
  - update module contract/map/change summary links to include initial temperature seed but not UI ownership.
- [x] Update `lib/src/di/service_locator.dart`:
  - register `CabinTemperatureCubit(locator<HvacService>())`.
- [x] Update `lib/src/di/app_bloc_providers.dart`:
  - add module contract if absent.
  - provide `CabinTemperatureCubit` from locator.
- [x] Update `lib/src/presentation/screens/heat/components/cabin_temperature_display.dart`:
  - add module contract if absent.
  - read `CabinTemperatureCubit` state and display `state.celsius`.
- [x] Run:
  - `flutter test test/unit/mode_cubit_test.dart`
  - `flutter test test/widget/cabin_temperature_display_test.dart`

## Task 5: Documentation and verification

- [x] Update `docs/development-plan.xml`:
  - resolve deferred multi-listener trigger.
  - update `M-HVAC`, `M-AUTO-HEAT`, `M-DI`, `M-BLOC-PROVIDERS`, `M-UI-HEAT`.
  - add `M-CABIN-TEMPERATURE`.
  - add Phase-4 step for FA-003 as done.
- [x] Update `docs/knowledge-graph.xml`:
  - add `M-CABIN-TEMPERATURE` and CrossLinks for HvacService listeners, AutoHeatService subscription, UI reads.
- [x] Update `docs/verification-plan.xml`:
  - add `V-M-CABIN-TEMPERATURE`.
  - update `V-M-HVAC`, `V-M-AUTO-HEAT`, `V-M-MODE`, `V-M-UI-HEAT`, `V-M-BLOC-PROVIDERS` scenarios.
- [x] Update `docs/functional-audit-findings.md`:
  - mark FA-003 addressed in Phase-4 Slice-3.
- [x] Run `grace lint --profile standard`.

## Task 6: Full verification and commit

- [x] Run `dart format` on modified Dart files.
- [x] Run `flutter test`.
- [x] Run `flutter analyze`.
- [x] Run `grace lint --profile standard`.
- [x] Run `git diff --check`.
- [x] Commit once: `git commit -m "Add cabin temperature state flow"`.
