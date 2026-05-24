# Phase-4 Slice 4 Auto Heat Sensor Noise Design

## Goal
Fix FA-005 so repeated HVAC temperature events inside the same effective auto-heat plan do not restart the 3→2→1→0 schedule.

## Decisions

1. AutoHeatService separates explicit starts from sensor updates.
   - `startAutoHeat(...)` is an explicit mode/settings transition and may restart from level 3.
   - HVAC/listener temperature updates are passive and only restart when the effective plan changes.
2. AutoHeatService stores a per-user effective plan key for the currently scheduled plan.
3. The plan key is derived from the actual sequence that will run:
   - `off` when `_getSequence(userType)` returns `null`.
   - `sequence:<level3>,<level2>,<level1>` when a sequence exists.
4. For fallback TemperatureConstants, different temperatures in the same range produce the same key and do not restart.
5. For ManualHeatSettings, repeated temperatures below the same threshold produce the same key and do not restart.
6. Transition to a different plan key is a controlled restart:
   - active sequence → different sequence: cancel old timer, callback(3), schedule new timers.
   - active sequence → off: cancel old timer, callback(0), no new timer.
   - off → off repeated: no extra callback(0).
7. `stopAutoHeat` and `dispose` clear stored plan keys. Explicit `startAutoHeat` clears the key before evaluating, preserving idempotent restart behavior.

## Data flow

`HvacService` temperature listener → `AutoHeatService._handleCabinTemperature` → `_updateAutoHeatForAllUsers(allowSamePlanRestart: false)` → per-user plan-key comparison → restart only on changed key.

`ModeCubit.setMode(auto)` / `applyPreset(auto)` → `AutoHeatService.startAutoHeat(...)` → `_updateAutoHeatForUser(allowSamePlanRestart: true)` → explicit restart.

## Error handling

No new external errors. Existing null-temperature behavior remains unchanged: no callback and no timer.

## Testing

- Repeated same-range sensor events do not append duplicate level 3 or reset timers.
- Transition to a different fallback sequence restarts once.
- Repeated off events publish callback(0) only once.
- Explicit repeated `startAutoHeat` still restarts from level 3.
- Custom ManualHeatSettings repeated below-threshold events do not restart.

## Scope exclusions

- Hysteresis around boundaries remains deferred; this slice only guards identical effective plan keys.
- Debounce/throttle windows are not added.
- Background-service lifecycle is FA-006 and remains separate.
