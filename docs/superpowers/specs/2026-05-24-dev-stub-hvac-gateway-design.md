# Dev-Stub HvacGateway for Local Development

> **Status:** Brainstorming paused mid-flow. Scope locked; approach proposed but not yet approved. Ready to resume from "Approaches" section in a new session.
>
> **Owner / Date:** Kirill Mikhaylin, 2026-05-24
>
> **Brainstorm checklist progress:**
> - [x] Explore project context
> - [x] Clarifying questions
> - [ ] Propose 2–3 approaches with trade-offs ← next step
> - [ ] Present design by sections
> - [ ] Write & commit spec (this file)
> - [ ] Self-review + user review
> - [ ] Hand off to `superpowers:writing-plans`

## Goal

Enable local development of AutoHeat **without a real Changan head unit** by providing a stub HVAC gateway that:

1. Replaces `HvacService` in the UI isolate when a dev flag is set.
2. Lets the developer **manually inject cabin temperature** (e.g. 5°C) and exercise the full `AutoHeatService` schedule, `ModeCubit` → `ModeService` persistence, and `CabinTemperatureCubit` UI updates.
3. Leaves the production code path **byte-equivalent** to the current build when the dev flag is off (const-folding by Dart compiler).
4. Does **not** modify the local `android_automotive_plugin` package.

## Problem (why this is needed)

When the app is launched on a regular Android emulator or device (UNI-S, Pixel_7_Pro_Large, etc.), the native side has no `Car` service, so `CarHvacManager` inside `AndroidAutomotivePlugin` stays null. Every HVAC call throws NPE on the Java side **but the plugin swallows it silently** and returns `null`/`0` to Dart. Concrete observed effects:

- `HvacService.getCabinTemperature` computes `(0 - 84) / 2 = -42.5°C` and treats it as a valid reading.
- `_publishCabinTemperature(-42.5)` fans the value out to listeners.
- `AutoHeatService` interprets `-42.5°C` as "very cold cabin" and starts a heat schedule (level 3 → 2 → 1 → 0).
- Real `ModeService` persists fake levels into `SharedPreferences`.
- The whole app looks "running" but is operating on garbage.

The existing `HvacService.getCabinTemperature` `try { ... } catch (_) { return 20.0; }` fallback does **not** trigger because the plugin doesn't rethrow — it returns a successful MethodChannel response with a default value.

## Scope (locked)

User explicitly opted for the **minimum-viable variant ("a")**:

- ✅ Temperature injection from a dev panel in the UI.
- ✅ `setSeatHeatLevel` is no-op (logged) in dev.
- ✅ `getCabinTemperature` returns the current dev-panel value (default 20°C).
- ✅ `_publishCabinTemperature`-equivalent fan-out so `AutoHeatService` + `CabinTemperatureCubit` see the injected value.
- ❌ Background service is **not started** in dev mode (early `return` under the flag).
- ❌ Ignition simulation — out of scope.
- ❌ Mirror of `setSeatHeatLevel` calls in the dev panel UI — out of scope.
- ❌ Plugin write-error simulation — out of scope.

### What stays untested locally (the "10%" we accept)

Recorded so we don't pretend it works locally. All of these continue to require a real head unit (or future expansion of the dev harness):

1. **Foreground service / `my_foreground` notification, id 888.**
2. **App-backgrounded behavior** — UI isolate carries the heat pipeline in dev; closing the UI stops the schedule.
3. **`autoStartOnBoot`** on head reboot.
4. **Two-isolate `ModeCubit` sync** through `SharedPreferences` (the `automotive_connected` flag, ignition-driven mode writes).
5. **`BackgroundRuntimeController`** lifecycle — `markStarted`, `handleStartFailure`, restart-backoff (`_maxRestartAttempts = 3`), `registerStopHandler`, `stopBackgroundService`.
6. **Ignition-driven start/stop** (`plugin.onCarSensorEventCallback` → `handleIgnition`) — including the **safety-critical** "ignition OFF → setHeatLevel(*, 0)" path.
7. **`onHvacChangeEventCallback` push channel** — only the publish target is exercised, the event-parsing branch is not.
8. **Plugin write failures** (recent change "Propagate HVAC plugin write failures" relies on `rethrow` in `setSeatHeatLevel`).
9. **Sensor-noise / effective-plan guard** (`_planKeyFor`, `_activePlanKeys`) — covered by unit tests but no live noise in dev.
10. **Real `AndroidAutomotivePlugin.connect()` race conditions.**

If any of these become important to exercise locally, expand the dev harness in a follow-up (would graduate to variant "b" or "c" from the original options matrix below).

## Investigation summary (so resuming session has full context)

### Why agent's earlier run "succeeded" while user's VSCode debug "failed"

There was **no functional difference**. Both runs produced identical NPE stack traces and the same `-42.5°C` bogus reading. The perceived difference was VSCode's Dart debugger pausing on caught exceptions (Run-and-Debug → Breakpoints → `All Exceptions` toggle).

**Workaround for VSCode noise (not part of this design, but worth doing alongside):**

- Uncheck `All Exceptions` in `Run and Debug → Breakpoints`.
- Or set `dart.debugSdkLibraries: false` + `dart.debugExternalPackageLibraries: false` in workspace `settings.json`.

### Current touch points in code

| File | Relevance |
| --- | --- |
| `lib/src/services/hvac_service.dart` | The class we abstract. `_androidAutomotivePlugin` exposed via `androidAutomotivePlugin` getter (used by background). |
| `lib/src/services/auto_heat_service.dart` | `setTemperature(double)` already exists as "тесты/диагностика" — but it bypasses `HvacService` listener chain, so `CabinTemperatureCubit` UI does **not** update via it. Insufficient as a dev-harness entry point. |
| `lib/src/services/background_service.dart` | `onStart` does `locator<HvacService>().androidAutomotivePlugin` then `plugin.connect()` and `plugin.onCarSensorEventCallback = controller.handleIgnition`. **Must early-return under dev flag** or it will NPE in the background isolate too. |
| `lib/src/di/service_locator.dart` | Where the dev-vs-prod branch lives. Currently registers concrete `HvacService`; needs to register the interface and pick implementation. |
| `lib/main.dart` | Calls `setupServiceLocator()` and `initializeBackgroundService()`. Needs the dev-flag guard for the latter. |
| `lib/src/presentation/screens/heat/heat_screen.dart` | Likely host for the dev panel (above or below `CabinTemperatureDisplay`). |

### GRACE artifacts that will need updating

- `docs/knowledge-graph.xml` — new module `M-DEV-HARNESS` + `CrossLink` from `M-DI` and from `M-DEV-HARNESS` to `M-HVAC`. Update `M-HVAC` `MAP_MODE` to reflect interface extraction.
- `docs/development-plan.xml` — new phase/slice for this work.
- `docs/verification-plan.xml` — verification entries for the new module (`V-M-DEV-HARNESS`), updated entries for `V-M-HVAC` and `V-M-DI` if their contracts move.
- `docs/operational-packets.xml` — if any packet references `HvacService` concretely instead of an interface.

## Approaches considered (none approved yet — paused before user review)

### (a) Interface + DI branch + dev panel — **recommended**

Extract a public interface from `HvacService`. Add a `FakeHvacService` implementation under `lib/src/services/dev/`. DI branch on `const bool.fromEnvironment('AUTOHEAT_DEV_STUB')`. Add a dev-only widget in `HeatScreen` that calls `fakeHvac.injectCabinTemperature(°C)`.

**Pros:**

- Plugin: 0 changes.
- Prod path: const-folded out → release-mode bytecode unchanged.
- Clean separation; `FakeHvacService` reuses real `_publishCabinTemperature` semantics so `AutoHeatService`, `CabinTemperatureCubit`, `ModeService` all behave as in prod.
- Easy to expand later (mirror, errors, ignition).

**Cons:**

- Requires introducing an interface where there is currently a concrete class — touches DI registrations and constructors of consumers (`ModeCubit`, `CabinTemperatureCubit`, `AutoHeatService.initialize`).
- Background service needs a dev-only early-return to avoid NPE in its isolate.
- GRACE artifacts need a new module entry (`M-DEV-HARNESS`).

### (b) Decorator around real `HvacService`

Keep `HvacService` concrete; wrap it in a `DevHvacDecorator` that intercepts reads and swallows the broken native call, optionally exposing `inject(...)`. DI swaps the registration.

**Pros:** No interface extraction. **Cons:** Still constructs the real `AndroidAutomotivePlugin` underneath (wasted lifecycle in dev; still NPE-spam unless decorator short-circuits before delegating; muddier semantics).

### (c) `DevHarnessService` as a side-channel

Add a service that has open access to `HvacService._publishCabinTemperature` (via making the method `@visibleForTesting` or `package:` visibility). No interface; just an extra dev-only injector.

**Pros:** Smallest surface area. **Cons:** Pollutes prod `HvacService` API with a test-only seam; doesn't solve the "plugin still gets constructed and NPEs" problem.

**Recommendation:** (a). All later sections assume (a) until the user picks otherwise.

## Proposed design (variant "a") — NOT YET APPROVED

### Components

1. **`HvacGateway` interface** in `lib/src/services/hvac_gateway.dart`:

   ```dart
   abstract class HvacGateway {
     Future<void> initialize();
     Future<void> setSeatHeatLevel(UserType userType, int level);
     Future<double> getCabinTemperature();
     double? get lastCabinTemperature;
     void addCabinTemperatureListener(CabinTemperatureListener l, {bool emitCurrent = false});
     void removeCabinTemperatureListener(CabinTemperatureListener l);
     void dispose();
   }
   ```

   `HvacService implements HvacGateway` — no behavioral change.

   **Open question:** what to do with `androidAutomotivePlugin` getter (used only by `background_service.dart`)? Two options:
   - Keep on `HvacService` only, not on `HvacGateway` — then `background_service.dart` casts or fetches `HvacService` directly. (Cleaner interface, slightly more coupling.)
   - Expose on the interface — but then `FakeHvacService` has to return something. (Probably worse.)
   - Recommended: keep on concrete class; background service no-ops in dev anyway.

2. **`FakeHvacService implements HvacGateway`** in `lib/src/services/dev/fake_hvac_service.dart`:

   - `_currentCelsius = 20.0` initial.
   - `setSeatHeatLevel`: `Logger.info(...)` + complete.
   - `getCabinTemperature`: returns `_currentCelsius`, publishes to listeners.
   - `addCabinTemperatureListener` / `removeCabinTemperatureListener`: same `Set<CabinTemperatureListener>` semantics as real service.
   - `_publishCabinTemperature(double)`: identical to real one (same logger marker for trace parity).
   - **`injectCabinTemperature(double celsius)`** — new public method, sole reason `FakeHvacService` is concrete (vs. `HvacGateway`): updates `_currentCelsius`, calls `_publishCabinTemperature`.

3. **DI branch** in `lib/src/di/service_locator.dart`:

   ```dart
   const _devStub = bool.fromEnvironment('AUTOHEAT_DEV_STUB', defaultValue: false);

   if (_devStub) {
     final fake = FakeHvacService();
     _registerSingletonIfAbsent<HvacGateway>(() => fake);
     _registerSingletonIfAbsent<FakeHvacService>(() => fake); // for the dev panel
   } else {
     _registerSingletonIfAbsent<HvacGateway>(() => HvacService());
   }
   ```

   All consumers (`ModeCubit`, `CabinTemperatureCubit`, `AutoHeatService.initialize`) take `HvacGateway` instead of `HvacService`.

4. **Background-service dev-bypass** in `lib/main.dart` (or `initializeBackgroundService`):

   ```dart
   if (!_devStub) {
     await initializeBackgroundService();
   }
   ```

   Also early-return inside `onStart` as a defense in depth (in case the system tries to autostart the service from a previous install).

5. **Dev panel** — small widget in `lib/src/presentation/screens/heat/components/dev/cabin_temperature_dev_panel.dart`:

   - Render only when `_devStub` is true.
   - Five preset chips (`−10°C`, `0°C`, `5°C`, `15°C`, `25°C`) — covers cold-start, freezing, cool, comfortable, warm.
   - On tap: `getIt<FakeHvacService>().injectCabinTemperature(value)`.
   - Placement: below `CabinTemperatureDisplay` so it's always visible.

### Data flow (dev mode)

```
DevPanel chip tap
  → FakeHvacService.injectCabinTemperature(value)
  → FakeHvacService._publishCabinTemperature(value)
  → fan-out to listeners:
      ├─ CabinTemperatureCubit → emits new state → CabinTemperatureDisplay updates UI
      └─ AutoHeatService._handleCabinTemperature → _updateAutoHeatForAllUsers
            → real Timer-cascade 3→2→1→0
            → real ModeService.setHeatLevel (SharedPreferences write)
            → real ModeCubit emits → SeatBlock UI updates
            → fakeHvac.setSeatHeatLevel(...) → log no-op
```

Identical to prod data flow up to the final step.

### Error handling

- `FakeHvacService` never throws from any method.
- Listener exceptions caught and logged (same as real `_publishCabinTemperature`).
- DI branch is total; no path where a `HvacGateway` request returns null.

### Testing

- Existing tests already use `FakeHvacService` (under `test/_helpers/`). Audit whether they can converge with the new prod-eligible fake — possibly yes, then `test/_helpers/fake_hvac_service.dart` is removed in favor of importing the prod one.
- New widget test: dev panel chip tap → `CabinTemperatureCubit` state updates → `CabinTemperatureDisplay` text changes.
- New widget test: dev panel only renders under `--dart-define=AUTOHEAT_DEV_STUB=true` (use `appendDartDefines` or test runner config).
- Existing `auto_heat_service` tests cover the schedule logic — no changes expected.
- `flutter analyze` clean; full `flutter test` green.

### Tooling

- Add a VSCode launch configuration in `.vscode/launch.json`:

  ```json
  {
    "name": "autoheat (dev stub)",
    "request": "launch",
    "type": "dart",
    "toolArgs": ["--dart-define=AUTOHEAT_DEV_STUB=true"]
  }
  ```

- (Optional) Same for JetBrains/Android Studio (`.run/*.run.xml`).

### GRACE impact

| Artifact | Change |
| --- | --- |
| `knowledge-graph.xml` | Add `M-DEV-HARNESS` (TYPE=UTILITY, STATUS=implemented, depends `M-HVAC` interface, `M-LOGGER`). CrossLink `M-DI → M-DEV-HARNESS` (`selects-impl-when-dev-flag-set`). |
| `knowledge-graph.xml` | Update `M-HVAC` MODULE_MAP to reflect `HvacGateway` interface extraction and `HvacService implements HvacGateway`. |
| `development-plan.xml` | New slice (Phase-? Slice-?) "Dev-Stub HvacGateway"; sub-tasks per file change. |
| `verification-plan.xml` | New `V-M-DEV-HARNESS` entry; update `V-M-HVAC` and `V-M-DI` references. |
| `operational-packets.xml` | Check whether any packet contract names `HvacService` concretely; switch to `HvacGateway` where appropriate. |
| Module headers | Update `HvacService`, `ServiceLocator`, `AutoHeatService`, `BackgroundService`, `ModeCubit`, `CabinTemperatureCubit`, `HeatScreen` MODULE_CONTRACT `DEPENDS` lines (and CHANGE_SUMMARY bumps). |

## Open questions (to resolve when resuming)

1. **`androidAutomotivePlugin` getter** — keep on concrete `HvacService` or expose on `HvacGateway`? (Recommendation in design above: keep on concrete.)
2. **Dev panel placement & visual** — below `CabinTemperatureDisplay`? Floating? Hidden behind long-press? Confirm with user before implementing.
3. **Preset temperatures** — −10/0/5/15/25 enough? Or also `°C` slider for finer control?
4. **`FakeHvacService` consolidation with `test/_helpers/fake_hvac_service.dart`** — merge or keep separate? Different concerns: tests want recording capabilities; dev panel wants UI-triggered injection. Likely separate but share an interface.
5. **Background-service dev-bypass placement** — wrapper in `main.dart`, or guard inside `initializeBackgroundService` itself? Latter is more defensive but couples the function to the dev flag.

## How to resume in a new session

1. Read this file end-to-end.
2. Re-check current state of the touched files (they may have changed since 2026-05-24):
   - `lib/src/services/hvac_service.dart`
   - `lib/src/services/auto_heat_service.dart`
   - `lib/src/services/background_service.dart`
   - `lib/src/di/service_locator.dart`
3. Resume brainstorming skill checklist at **"Propose 2–3 approaches with trade-offs"** — present the three options from this doc to the user (they are not yet approved).
4. After user approval on approach (a), proceed to "Present design by sections" (this doc covers them already — confirm each section with the user incrementally).
5. Self-review the spec (this doc) for any stale information vs. current code.
6. Hand off to `superpowers:writing-plans` for implementation step-by-step.

## Scope exclusions (no creep)

- Not fixing the underlying plugin NPE (would touch `packages/android_automotive_plugin`, which CLAUDE.md says to keep as upstream snapshot).
- Not adding sanity bounds to `HvacService` (separate defensive change; can be done independently).
- Not refactoring `AutoHeatService.setTemperature(double)` (orthogonal; the diagnostic path stays as-is).
- Not addressing the untracked test failure in `test/widget/heat_screen_manual_level_selector_test.dart` (`find.text('off') findsNothing`) — separate issue, pre-existed this work.

## Related but separate

- VSCode "All Exceptions" breakpoint noise — solvable independently of this design by toggling the breakpoint setting; document in onboarding notes.
- `ManualHeatLevelSelector` layout fix (Stack overlay in `heat_screen.dart` v1.2.0, 2026-05-24) — unrelated; already shipped in this session.
