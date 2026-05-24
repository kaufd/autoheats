# Phase-4 Slice 4 Auto Heat Sensor Noise Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Fix FA-005 so repeated same-plan temperature events do not restart auto heat schedules.

**Architecture:** AutoHeatService stores a per-user effective plan key (`off` or sequence durations) and compares it on passive sensor updates. Explicit `startAutoHeat(...)` clears the stored key and preserves restart/idempotency semantics.

**Tech Stack:** Flutter/Dart, flutter_test, fake_async, existing FakeHvacService and LoggerTestSink, GRACE semantic docs.

---

## Files

- Modify: `test/unit/auto_heat_service_test.dart`
- Modify: `lib/src/services/auto_heat_service.dart`
- Modify: `docs/development-plan.xml`
- Modify: `docs/verification-plan.xml`
- Modify: `docs/functional-audit-findings.md`
- Modify: `docs/superpowers/plans/2026-05-22-phase-4-slice-4-auto-heat-sensor-noise.md`

---

## Task 1: Same-plan sensor guard tests

- [x] Add failing tests to `test/unit/auto_heat_service_test.dart`:
  - `scenario-13`: repeated same fallback range sensor events do not restart sequence.
  - `scenario-14`: transition to different fallback sequence restarts once.
  - `scenario-15`: repeated off events emit callback(0) once.
  - `scenario-16`: explicit repeated startAutoHeat still restarts from level 3.
  - `scenario-17`: custom ManualHeatSettings repeated below-threshold events do not restart.
- [x] Run `flutter test test/unit/auto_heat_service_test.dart` and confirm RED: same-range repeated events currently append duplicate `3` and reset timers.

## Task 2: Implement plan-key guard

- [x] In `lib/src/services/auto_heat_service.dart`, update module contract/map/change summary to include FA-005.
- [x] Add per-user state:
  - `final Map<UserType, String> _activePlanKeys = {};`
- [x] Add helper:
  - `_planKeyFor(HeatSequence? sequence) => sequence == null ? 'off' : 'sequence:${sequence.level3Duration},${sequence.level2Duration},${sequence.level1Duration}'`
- [x] Change `_handleCabinTemperature` and `setTemperature` passive updates to call `_updateAutoHeatForAllUsers(allowSamePlanRestart: false)`.
- [x] Change `startAutoHeat` to remove `_activePlanKeys[userType]` before `_updateAutoHeatForUser(userType, allowSamePlanRestart: true)`.
- [x] Change `_updateAutoHeatForUser` to:
  - compute `sequence` and `planKey`;
  - if `!allowSamePlanRestart && _activePlanKeys[userType] == planKey`, return without cancelling timers or invoking callback;
  - otherwise update `_activePlanKeys[userType] = planKey`, cancel existing timer, and apply off/sequence behavior.
- [x] Clear `_activePlanKeys` in `stopAutoHeat` and `dispose`.
- [x] Run `flutter test test/unit/auto_heat_service_test.dart` and confirm GREEN.

## Task 3: Docs and verification

- [x] Update `docs/development-plan.xml` Phase-4 with FA-005 done step.
- [x] Update `M-AUTO-HEAT` notes to mention effective plan key guard and explicit start restart semantics.
- [x] Update `docs/verification-plan.xml` `V-M-AUTO-HEAT` with scenarios 13-17 and assertions.
- [x] Update `docs/functional-audit-findings.md` FA-005 status/resolution and suggested ordering.
- [x] Run `grace lint --profile standard`.

## Task 4: Full verification and commit

- [x] Run `dart format lib/src/services/auto_heat_service.dart test/unit/auto_heat_service_test.dart`.
- [x] Run `flutter test`.
- [x] Run `flutter analyze`.
- [x] Run `grace lint --profile standard`.
- [x] Run `git diff --check`.
- [x] Commit once: `git commit -m "Guard auto heat against sensor noise"`.
