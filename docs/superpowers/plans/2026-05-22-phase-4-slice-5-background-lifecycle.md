# Phase-4 Slice 5 Background Lifecycle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Fix FA-006 by hardening background-service stop, ignition OFF, and restart-backoff behavior behind a testable controller.

**Architecture:** Keep `onStart(ServiceInstance)` as a thin plugin/DI adapter. Add `BackgroundRuntimeController` with fake-friendly service/mode ports; it owns stop command handling, awaited seat shutdown, ignition handling, and bounded restart-backoff.

**Tech Stack:** Flutter/Dart, flutter_test, fake_async, flutter_background_service interfaces, existing Logger.

---

## Files

- Create: `lib/src/services/background_runtime_controller.dart`
- Create: `test/unit/background_runtime_controller_test.dart`
- Modify: `lib/src/services/background_service.dart`
- Modify: `docs/development-plan.xml`
- Modify: `docs/verification-plan.xml`
- Modify: `docs/functional-audit-findings.md`

---

## Task 1: Runtime controller tests

- [x] Create failing tests in `test/unit/background_runtime_controller_test.dart`:
  - `scenario-1`: stop command shuts down driver/passenger and calls `stopSelf()`.
  - `scenario-2`: ignition OFF awaits driver/passenger level 0 before completing.
  - `scenario-3`: malformed ignition event is ignored and does not throw.
  - `scenario-4`: start failure below max schedules retry after configured delay.
  - `scenario-5`: start failure at max attempts calls `stopSelf()` and does not retry.
- [x] Run `flutter test test/unit/background_runtime_controller_test.dart` and confirm RED because controller file is missing.

## Task 2: Implement BackgroundRuntimeController

- [x] Create `lib/src/services/background_runtime_controller.dart` with GRACE module contract linked to `M-BACKGROUND`, `V-M-BACKGROUND`, `FA-006`.
- [x] Define ports:
  - `abstract class BackgroundServicePort`
  - `class ServiceInstanceBackgroundServicePort`
  - `abstract class BackgroundModePort`
  - `class ModeCubitBackgroundModePort`
- [x] Implement `BackgroundRuntimeController`:
  - `registerStopHandler()` subscribes to `servicePort.on('stopService')`.
  - `stopService()` awaits `shutdownSeats(trigger: 'stopService')` then `stopSelf()`.
  - `shutdownSeats(...)` attempts driver and passenger sequentially; logs per-seat failure and continues.
  - `handleIgnition(CarSensorEvent)` awaits shutdown on OFF, no-ops on ON, catches malformed events.
  - `handleStartFailure(...)` increments attempts, sets notification and schedules retry below max; calls `stopSelf()` at max.
  - `dispose()` cancels stop subscription and pending retry timer.
- [x] Run `flutter test test/unit/background_runtime_controller_test.dart` and confirm GREEN.

## Task 3: Wire background_service.dart to controller

- [x] Update `lib/src/services/background_service.dart` module contract/map/change summary.
- [x] Replace direct stop/ignition logic with `BackgroundRuntimeController`:
  - create controller after `setupServiceLocator()`;
  - call `registerStopHandler()`;
  - set plugin sensor callback to `controller.handleIgnition`;
  - use helper `_startRuntimeConnection(controller, plugin)` for connect/prefs startup;
  - in catch, call `controller.handleStartFailure(... retry: () => _startRuntimeConnection(...))`.
- [x] Update `stopBackgroundService()` to await controller shutdown when available before invoking `stopService`.
- [x] Run `flutter analyze lib/src/services/background_service.dart lib/src/services/background_runtime_controller.dart`.

## Task 4: Docs and verification

- [x] Update `docs/development-plan.xml` Phase-4 with FA-006 done step and M-BACKGROUND notes.
- [x] Update `docs/verification-plan.xml` V-M-BACKGROUND with new unit test file/scenarios and manual smoke checklist.
- [x] Update `docs/functional-audit-findings.md` FA-006 status/resolution and suggested ordering.
- [x] Run `grace lint --profile standard`.

## Task 5: Full verification and commit

- [x] Run `dart format lib/src/services/background_service.dart lib/src/services/background_runtime_controller.dart test/unit/background_runtime_controller_test.dart`.
- [x] Run `flutter test`.
- [x] Run `flutter analyze`.
- [x] Run `grace lint --profile standard`.
- [x] Run `git diff --check`.
- [x] Commit once: `git commit -m "Harden background service lifecycle"`.
