# Mode-Source Decoupling: Auto vs User Presets

> **Status:** Approved 2026-05-24. Ready for `superpowers:writing-plans`.
>
> **Owner / Date:** Kirill Mikhaylin, 2026-05-24
>
> **Brainstorm checklist progress:**
> - [x] Explore project context
> - [x] Confirm architecture in chat (no formal Q&A — pre-discussed)
> - [x] Present design summary by sections
> - [x] User approval
> - [x] Write & commit spec (this file)
> - [ ] Self-review + user review ← next
> - [ ] Hand off to `superpowers:writing-plans`

## Goal

Untangle two conceptually independent things that today share state and overwrite each other:

- **System auto-heat** (`HeatMode.auto`) — our built-in algorithm with fixed settings, behaves like a hidden preset the user cannot edit.
- **User preset** (`HeatMode.presets`) — algorithm runs against the user's `selectedPreset.settings`.

Currently every preset apply copies `preset.settings` into `ManualSettingsCubit`, which is the same store `AutoHeatService` reads for the system auto-mode. After any user preset apply, the "system" auto-mode no longer behaves as the system intended — it inherits the last applied preset's thresholds and durations. This spec eliminates that intersection.

## Background — current code

- `HeatMode` enum already has all three values: `manual`, `presets`, `auto` (`lib/src/app_enums.dart:24`).
- `ModeToggler` already sends those three values to `ModeCubit.setMode(...)` (`lib/src/presentation/screens/heat/components/mode_toggler.dart`).
- `ModeCubit._manageAutoHeat` only starts `AutoHeatService` when `mode == auto`. `mode == presets` does **not** drive the algorithm — it is today a static `(heatMode, heatLevel)` snapshot.
- `ModeCubit._startAutoHeat` reads `ManualSettingsService.getSettings(user)` and passes that into `AutoHeatService.startAutoHeat(user, cb, settings: ...)`.
- `AutoHeatService.startAutoHeat(...)` accepts `settings?`. When `settings == null` it already falls back to `TemperatureConstants.getHeatSequence` inside `_getSequence` (`lib/src/services/auto_heat_service.dart:250`). So "system defaults" are already encoded in `TemperatureConstants`; we don't need a separate hidden-preset object.
- `AppContent._applyPreset` does three things: `ManualSettingsCubit.applyPresetSettings(...)` (the leak), `ModeCubit.applyPreset(preset)`, `PresetCubit.applyPreset(preset)`. Today only the first one actually feeds the algorithm.
- `Preset` carries `heatMode` and `heatLevel` snapshot fields. `ModeCubit.applyPreset` reads `preset.heatMode`; when the preset was saved while the user was in `auto`, this is `auto` and `_manageAutoHeat` then starts the algorithm with the (just-overwritten) `ManualSettingsCubit`. Net effect: a user preset only "really applies" because of the leak.

## Decisions (all approved)

### 1. Settings source per `HeatMode`

| `HeatMode` | how `AutoHeatService` is wired |
|---|---|
| `manual` | `stopAutoHeat(user)` — algorithm off |
| `auto` | `startAutoHeat(user, cb)` **without** `settings:` — `_getSequence` uses `TemperatureConstants.getHeatSequence` |
| `presets` | `startAutoHeat(user, cb, settings: selectedPreset.settings)` — algorithm reads the user-specified preset |

System defaults are **not** a stored object. They are the existing `TemperatureConstants.getHeatSequence` lookup. No new "hidden preset" entity is introduced.

### 2. `ModeCubit` API changes

- Drop `ManualSettingsService` dependency from the constructor.
- Add a way for `ModeCubit._startAutoHeat` to get the active preset's settings when entering `HeatMode.presets`. **Implementation choice (decided in plan phase, see Open Q1)**:
  - Variant A: `ModeCubit` takes `PresetService` as a constructor dep and calls `getPresetById(selectedPresetId, user)` on demand.
  - Variant B: `ModeCubit.setMode(user, presets, {ManualHeatSettings? settings})` accepts settings from the caller. `AppContent` / `ModeToggler` callback resolves them via `PresetCubit.state` before calling.
  - Recommendation: B — keeps `ModeCubit` free of preset persistence and avoids a duplicate read path. `PresetCubit` already holds the canonical in-memory `selectedPresets[user]`.
- `applyPreset(preset)`:
  - Always emits `HeatMode.presets` (does **not** read `preset.heatMode`).
  - Starts `AutoHeatService` with `preset.settings` directly from the argument.
  - Does **not** persist `selectedPresetId` — that stays owned by `PresetCubit.applyPreset` (single-writer principle, see Open Q2).
  - Does **not** read or use `preset.heatLevel`. Initial level is whatever the algorithm computes from current cabin temperature on the immediate sequence start.
- `setMode(user, presets)`:
  - Requires settings (Variant B). If caller passes `null` → no-op (defensive; UI must not call this state).
- `setMode(user, auto)`:
  - Persists mode, `stopAutoHeat`, then `startAutoHeat(user, cb)` (no settings argument).
- `setMode(user, manual)`:
  - Persists mode, `stopAutoHeat`.
- `_initializeHeatModes` (cold-start restore):
  - `manual` → restore last `heatLevel` as today.
  - `auto` → `startAutoHeat(user, cb)` without settings.
  - `presets` → look up `PresetCubit.state.selectedPresets[user]`; if present, `startAutoHeat(user, cb, settings: ...)`. If `null` (selectedPresetId was cleared but mode was not), fall back to `setMode(user, manual)` + `setHeatLevel(user, 0)` so we never sit in "presets without a preset". This is the same fallback we use on delete-active-preset (decision 5).

### 3. `Preset` model

- Remove fields: `heatMode`, `heatLevel`.
- `Preset.settings` (`ManualHeatSettings`) and metadata (`id`, `name`, `userType`, `createdAt`, `lastUsed`) remain.
- Regenerate `preset.g.dart` via `dart run build_runner build --delete-conflicting-outputs`.
- `PresetService.createPresetFromCurrentSettings` loses `heatMode` and `heatLevel` parameters.
- `PresetCubit.savePreset(...)` loses `heatMode` and `heatLevel` parameters.
- `PresetsTab._onSave` loses the `modeCubit.getModeByUser` / `getHeatLevelByUser` reads.
- Existing saved presets in `SharedPreferences` decode without these keys (`json_serializable` ignores missing keys when there are no required-positional defaults). No data migration is needed; no rollback is supported (user-approved: legacy is dropped).

### 4. Removed modules

Delete:
- `lib/src/cubit/manual_settings_cubit.dart`
- `lib/src/services/manual_settings_service.dart`
- `ManualSettingsState` class from `lib/src/models/manual_settings.dart` (the file stays — `ManualHeatSettings` and `AutoHeatLevel` are still used by `Preset` and `AutoHeatService`).
- DI registrations in `lib/src/di/service_locator.dart` for both classes.
- `BlocProvider<ManualSettingsCubit>` in `lib/src/di/app_bloc_providers.dart`.
- `ManualSettingsCubit.initialize()` call in `PresetsTab.initState`.

`SharedPreferences` keys (`manual_settings_driver`, `manual_settings_passenger`) are left as dead data — read-side gone, no migration, no harm.

### 5. Apply / delete behavior

- **Apply** (`AppContent._applyPreset`):
  - Removes the `manualSettingsCubit.applyPresetSettings(...)` call.
  - Calls `ModeCubit.applyPreset(preset)` and `PresetCubit.applyPreset(preset)`. Both still write `selectedPresetId` today; we deduplicate (single source of truth = `PresetCubit`; `ModeCubit.applyPreset` reads `PresetCubit.state` for the id-set and just kicks the algorithm). Final wiring decided in plan phase (Open Q2).
  - Continues to show the SnackBar.
- **Delete active preset** (`PresetsTab._onDelete`):
  - If the deleted preset is `selectedPresets[user]` AND the user is currently in `HeatMode.presets` → `ModeCubit.setMode(user, manual)`, `ModeCubit.setHeatLevel(user, 0)`, then `ScaffoldMessenger.showSnackBar` with «Активный пресет удалён».
  - If the deleted preset is active but user is in `manual` or `auto` → no mode change. `selectedPresetId` clearing is already handled in `PresetService.deletePreset`.
  - SnackBar uses `themeColors.textButtonSelected` for text color (same contrast-safe token used by Save / Новый пресет).

### 6. `ModeToggler` "Пресеты" segment guard

`ModeToggler` gains a callback prop:

```dart
final VoidCallback? onPresetsTabRequested;
```

`AppContent` passes a function that calls `_selectTab(1)`. On tap of the `Пресеты` segment:

```dart
final selected = presetCubit.state.selectedPresets[user];
if (selected == null) {
  onPresetsTabRequested?.call();
  return; // segment does NOT latch
} else {
  modeCubit.setMode(user, HeatMode.presets, settings: selected.settings);
}
```

Segmented-button visual state continues to follow `ModeCubit.state` (no fake latching). This keeps the invariant "highlighted segment = actual current mode".

### 7. Verification updates

- `V-M-MODE`: refresh assertions — `applyPreset` always emits `HeatMode.presets`; `_startAutoHeat` settings argument routing covered.
- `V-M-MANUAL-SETTINGS`: deleted along with the module.
- `V-M-PRESET`: refresh — no `heatMode`/`heatLevel` in JSON contract.
- `V-M-UI-APP`: refresh — `_applyPreset` no longer touches ManualSettings.
- `V-M-UI-PRESETS`: refresh — delete-active-preset fallback scenario added.

## Affected tests

| file | change |
|---|---|
| `test/unit/manual_settings_cubit_test.dart` | delete |
| `test/unit/mode_cubit_test.dart` | rewrite apply / auto scenarios; remove `preset.heatMode` expectations |
| `test/unit/preset_service_test.dart` | drop `heatMode`/`heatLevel` from fixtures |
| `test/unit/preset_cubit_test.dart` | drop `heatMode`/`heatLevel` from fixtures |
| `test/unit/preset_model_test.dart` | drop `heatMode`/`heatLevel` from fixtures |
| `test/unit/service_locator_test.dart` | remove `ManualSettings*` registrations from expectations |
| `test/widget/settings_screen_test.dart` | drop `BlocProvider<ManualSettingsCubit>` from the test harness |
| `test/widget/white_theme_button_contrast_test.dart` | unchanged |
| `test/widget/app_smoke_test.dart` | unchanged (rebuilds via `app_bloc_providers`; should still pass after DI cleanup) |
| `test/widget/cabin_temperature_display_test.dart` | unchanged |

## What stays untouched

- `AutoHeatService` internal logic (`_getSequence` already handles both branches).
- `PresetService` CRUD.
- `Preset.settings` (`ManualHeatSettings`) / `AutoHeatLevel` model.
- Tab structure, tab order, ModeToggler visual chrome (only one callback added).
- Settings tab (slim form preserved).

## Open questions for plan phase

These are tactical, not architectural; the plan resolves them with concrete code:

1. **`ModeCubit._startAutoHeat` settings source** — Variant A (inject `PresetService`) vs Variant B (pass settings from caller). Recommended B.
2. **`selectedPresetId` single-writer** — currently both `ModeCubit.applyPreset` and `PresetCubit.applyPreset` write it. After this refactor we want one canonical writer. Recommended: `PresetCubit` owns it; `ModeCubit.applyPreset` reads from `PresetCubit.state` rather than writing the id itself.
3. **Order of operations in `AppContent._applyPreset`** — current code does `ModeCubit.applyPreset` before `PresetCubit.applyPreset`. After refactor `ModeCubit.applyPreset` needs `selectedPresetId` already set in `PresetCubit.state` to load preset.settings. Either swap order, or pass `preset.settings` directly into `ModeCubit.applyPreset(preset)` (more local, recommended).
4. **`ModeCubit._initializeHeatModes` for `HeatMode.presets`** — restore depends on `PresetCubit` having loaded `selectedPresets`. Today both initialize in parallel during app start. Plan resolves: either await `PresetCubit.loadAllPresets()` from `ModeCubit` init, or delay `ModeCubit._initializeHeatModes` until first frame after providers settle.

## Out of scope

- Reusing `ManualHeatSettings` as a "system preset" editable in Settings — explicitly rejected. System auto-heat is `TemperatureConstants`, not user-tunable.
- Per-user system defaults — `TemperatureConstants.getHeatSequence` is currently temperature-only, not user-typed. Stays as is.
- Migration of legacy persisted `manual_settings_*` keys — not needed; ignored.
- Preset auto-fill of `heatLevel` on apply — explicitly dropped (algorithm computes the initial level).

## Self-review checklist

- [x] No `TBD` / `TODO` / vague language
- [x] Internal consistency: every "removed" item appears nowhere as still-used; `selectedPresetId` single-writer contradiction between Decision 2 and Open Q2 fixed inline
- [x] Scope: single coherent refactor, no decomposition needed
- [x] Ambiguity: each requirement has a single interpretation; open-questions section explicitly flags the four tactical resolutions left for plan phase
