# Settings & Presets UX Redesign

> **Status:** Implemented 2026-05-24 (plan `docs/superpowers/plans/2026-05-24-settings-presets-ux-redesign.md`).
>
> **Owner / Date:** Kirill Mikhaylin, 2026-05-24
>
> **Brainstorm checklist progress:**
> - [x] Explore project context
> - [x] Clarifying questions (scope, layout, apply behavior, settings placement, edit/save semantics)
> - [x] Propose layout approaches with visual mockups (companion saved to `.superpowers/brainstorm/.../content/`)
> - [x] Present design by sections
> - [x] Write & commit spec (this file)
> - [ ] Self-review + user review ← next
> - [ ] Hand off to `superpowers:writing-plans`

## Goal

Solve two UX problems on the current Settings tab:

1. **Temperature visibility toggle is hidden below scroll** on the head-unit screen — users cannot discover it without scrolling.
2. **Preset workflow is split between two tabs**: configuration + save happens on "Настройки", listing + applying happens on "Пресеты". One mental model spans two screens, which is confusing.

Both problems are addressed by an information-architecture rework rather than minor placement tweaks.

## Decisions (all approved)

1. **Tab order changes** to `Управление → Пресеты → Настройки`. Settings tab moves to position 3 (least-used → rightmost).

2. **Merge preset configuration into "Пресеты" tab** (single workflow hub). Old preset-configuration block is removed from "Настройки".

3. **New "Пресеты" tab layout** (variant C + E + E1 from brainstorm):
   - Top: segmented control `[Driver] | Passenger`.
   - Left column: preset settings editor (sliders for threshold + 3 heat-level durations).
   - Right column: list of saved presets for the selected user. Each row has `[✎]` (edit) and `[▶]` (apply) icon-buttons.
   - Below the right list: `[+ Новый пресет]` button (opens editor in "zeros" empty state for naming a brand-new preset).
   - Below the editor (left column): `[Сохранить]` button — context-aware (see below).

4. **Editor content rules** (single uniform rule, no special states):
   - **Has active preset for this user** (e.g., user is in `HeatMode.presets`/`HeatMode.auto` and a preset is marked active): editor shows that preset's settings.
   - **No active preset** (user is in `HeatMode.manual`, or no presets exist at all): editor shows all sliders at **zero / default**. Empty/zero state is the same in both cases — no special placeholder text.

5. **Apply semantics (E1 — apply implicitly saves)**:
   - `[▶]` on a preset row applies it to HVAC. If the editor currently shows that preset and there are unsaved slider changes, save first, then apply (WYSIWYG: the running state matches the visible editor).
   - For all other cases, `[▶]` applies the preset's currently-saved settings.

6. **Edit-target switching when active preset changes (case 3-α)**:
   - If user is editing preset X (`[✎]` clicked, sliders moved, not saved) and a different preset Y becomes active (via `[▶]`), the editor jumps to Y immediately. Unsaved edits on X are **silently discarded**. Rationale: keeps the "editor shows active preset" rule consistent; explicit-edit branches (via `[✎]`) are clearly temporary.

7. **`[+ Новый пресет]`** opens the editor with the empty/zero state and an inline name-prompt at the top. `[Сохранить]` persists. This replaces the previous "Сохранить текущие настройки" button — there is no longer a separate "save current as new preset" affordance, because the editor IS the current preset.

8. **"Настройки" tab (slim, S1)**: only theme selector + cabin-temperature visibility switch. Two rows, no scroll. Designed to grow if future global settings appear (language, units, notifications, app version, etc.).

9. **HeatScreen unchanged.** Cabin temperature visibility toggle stays in "Настройки" — no in-place toggle on HeatScreen (S1 chosen, not S3).

## Architecture decision: Path A (user-approved 2026-05-24)

The current data model has **`ManualSettings`** (per-user, runtime settings used by `AutoHeatService`) as a separate concept from **`Preset`** records. The redesign blurs the distinction in UI: editor edits a preset record, but the runtime continues to use `ManualSettings`.

**Path A — minimal model change** (locked): editor binds to a `Preset` record (or to the "zero/default" form state when no preset is active). On `Save`, write to the preset record. On `Apply`, copy preset → `ManualSettings` (current behaviour preserved). The "current `ManualSettings` ≠ active preset record" divergence still exists at the data layer but is invisible in UI because the user only ever sees the preset record.

**Path B (rejected for this iteration)** would collapse `ManualSettings` into `Preset` and have `AutoHeatService` read directly from the active preset. Deferred: can be revisited if the gap becomes painful.

## Component breakdown

### `AppContent` (`lib/src/presentation/app_content.dart`)
- Reorder tabs: `HeatScreen → PresetsTab → SettingsScreen`.
- Replace `PresetsListScreen` integration with the new `PresetsTab`.
- `_applyPreset` stays as the bridge from UI to `ModeCubit.applyPreset` + `PresetCubit.applyPreset` + `ManualSettingsCubit.applyPresetSettings`. Logic mostly unchanged; just called from inside the new tab instead of from `PresetsListScreen.onPresetApplied`.

### New `PresetsTab` (`lib/src/presentation/screens/presets/presets_tab.dart`)
- Hosts the Driver/Passenger segmented control.
- Layout: `Row[Editor, ListPanel]` with equal halves and a vertical divider (same theme treatment as current `HeatScreen` divider).
- Local state (or a new `PresetsTabCubit`): `selectedUser: UserType`, `editingPresetId: String?` (null when editor reflects the active preset or zeros).

### `PresetEditor` widget (`lib/src/presentation/screens/presets/components/preset_editor.dart`)
- Reuses `ManualSettingsSection` logic (threshold slider + per-level duration sliders).
- Header line shows: editing target name + (★ if active) + `[Сохранить]` button.
- Driver/passenger comes from the parent's segmented control.
- "Empty/zero" state = all sliders at 0, name prompt visible if invoked by `[+ Новый пресет]`.

### `PresetList` widget (rewrite of existing `lib/src/presentation/screens/presets/components/preset_list.dart`)
- Vertical list of preset rows for the selected user.
- Each row: `[★?]  name  [✎]  [▶]`. Touch targets ≥ 44dp.
- Bottom of the list: `[+ Новый пресет]`.
- Active marker `★` = preset matches the user's currently-applied state.
- Editing marker `▸` (subtle highlight or border) = the preset whose record is currently in the editor.

### `SettingsScreen` (`lib/src/presentation/screens/settings/settings_screen.dart`)
- Remove "Настройки пресетов" header + `PresetsSection`.
- Remove `ManualSettingsCubit.initialize()` call from `initState` if no other widget on this screen needs it (verify: `PresetsTab` will need it instead → move the call).
- Keep theme row + temperature visibility row.
- Optionally widen typography / vertical spacing now that the screen is sparse (don't over-design — leave room to grow).

### Files no longer needed in current form
- `lib/src/presentation/screens/presets/presets_list_screen.dart` → absorbed into `PresetsTab`.
- `lib/src/presentation/screens/settings/components/presets_section.dart`, `presets_settings.dart`, `save_preset_dialog.dart`, `manual_settings_section.dart` → moved/restructured under `presets/` directory. (Decide on move-vs-rewrite per file during plan phase.)

## Data flow

### Tap `[✎]` on preset row
`PresetList` row tap → `PresetsTab` setState `editingPresetId = preset.id` → `PresetEditor` rebuilds with that preset's settings → user adjusts sliders (local editor state) → user clicks `[Сохранить]` → `PresetCubit.updatePreset(id, settings)` → list refreshes (no apply).

### Tap `[▶]` on preset row
`PresetList` row apply-tap → `PresetsTab._applyPreset(preset)`:
1. If `editingPresetId == preset.id` and editor is dirty: `await PresetCubit.updatePreset(...)` (save first).
2. `await ModeCubit.applyPreset(preset)` (which delegates to `HvacService`).
3. `await ManualSettingsCubit.applyPresetSettings(...)` (copy into runtime ManualSettings).
4. Update `editingPresetId` to follow the new active preset (case 3-α).

### Tap `[+ Новый пресет]`
`PresetsTab` setState → `editingPresetId = null`, `isNewPresetDraft = true` → editor renders zero/empty state with name prompt → user fills name + sliders → `[Сохранить]` → `PresetCubit.savePreset(...)`.

### Driver/Passenger switch
`PresetsTab` setState → `selectedUser` flips → list and editor both rebuild with the new user's data.

## Error handling

- All `PresetCubit` / `ModeCubit` / `ManualSettingsCubit` calls are awaited. Failures bubble up via the existing error states each cubit already exposes.
- Existing `ErrorBlock` widget continues to render in place of the list when `ManualSettingsCubit` fails to load.
- Apply-failure (HVAC write fails) — relies on the recent "propagate HVAC plugin write failures" change. Display via existing SnackBar pattern from `AppContent._applyPreset`.

## Testing

- Widget test: `PresetsTab` initial render shows zero-state editor when no active preset / no presets.
- Widget test: `[✎]` switches editor to that preset.
- Widget test: `[▶]` calls `ModeCubit.applyPreset` and updates `editingPresetId` to the applied preset.
- Widget test: editing preset X then applying Y → editor jumps to Y, X's unsaved edits discarded silently.
- Widget test: `[+ Новый пресет]` opens zero-state editor with name prompt.
- Widget test: `SettingsScreen` renders theme + temperature visibility above the fold on `Size(1920, 720)`.
- Widget test: tab order in `AppContent` is Управление → Пресеты → Настройки.
- Existing `app_smoke_test.dart` and other widget tests continue to pass.
- `flutter analyze` clean.

## GRACE impact

| Artifact | Change |
| --- | --- |
| `knowledge-graph.xml` | Rename/restructure `M-UI-SETTINGS` and `M-UI-PRESETS`: now `M-UI-PRESETS` is the merged tab module. `M-UI-SETTINGS` keeps only theme + temperature toggle. New `CrossLink M-UI-PRESETS → M-PRESET, M-MANUAL-SETTINGS, M-MODE`. Update `M-UI-APP` `depends` ordering. |
| `development-plan.xml` | New phase/slice "Settings & Presets UX Redesign"; sub-tasks per migration step. |
| `verification-plan.xml` | Refresh `V-M-UI-SETTINGS` and `V-M-UI-PRESETS` entries with new scenario list. |
| `operational-packets.xml` | Verify no packet contract still references the moved modules' old surface. |
| Module headers (CHANGE_SUMMARY bumps) | `settings_screen.dart`, `presets_list_screen.dart` (probably deleted), `presets_section.dart`, `presets_settings.dart`, `app_content.dart`, new `presets_tab.dart`. |

## Scope exclusions

- No change to the `Preset` data model (name, settings, userType, heatMode, heatLevel).
- No change to `ModeCubit.applyPreset` / `ManualSettingsCubit.applyPresetSettings` / `PresetCubit` semantics. We rearrange UI calls but keep cubits' contracts.
- No change to HeatScreen.
- No quick-toggle for temperature visibility on HeatScreen (option S3 was rejected).
- No drawer / no gear icon in AppBar (option S2 was rejected).
- Path B (collapsing `ManualSettings` into `Preset`) explicitly deferred; we go with Path A.
- Dev-stub `HvacGateway` work is orthogonal and tracked in its own spec (`2026-05-24-dev-stub-hvac-gateway-design.md`).

## Open questions for implementation phase

1. **`[+ Новый пресет]` name-prompt placement** — inline (above the editor) or modal dialog (current `SavePresetDialog` reused)? Inline is more discoverable, modal is consistent with current code. Decide during writing-plans.
2. **"Empty/zero" state visual** — sliders at 0 might look broken at first glance. Consider a faint "no preset selected — adjust sliders to create" hint above the editor, only shown when no preset is being edited. Decide during writing-plans.
3. **Icon set** — `[✎]` = `Icons.edit`, `[▶]` = `Icons.play_arrow` or `Icons.check_circle`? `play_arrow` reads as "play" which is the right verb here. Validate with running app screenshots.
4. **Marker overlap** — when the preset being edited IS the active one, both `★` and `▸` may want to appear. Decide visual treatment.
5. **`PresetsListScreen.onPresetApplied` callback** — this prop will be removed when the screen is dissolved. Verify nothing else hooks into it.

## How to resume in a new session

1. Read this file end-to-end.
2. Read companion mockups in `.superpowers/brainstorm/32576-1779628072/content/`:
   - `presets-tab-layout.html` — Q: layout C/B/A
   - `presets-tab-apply-question.html` — Q: apply semantics
   - `presets-tab-apply-v2.html` — Q: per-row icons + E1
   - `settings-placement.html` — Q: slim tab vs gear icon
   - `final-layout.html` — consolidated mockup
3. Verify current state of touched files (may have drifted):
   - `lib/src/presentation/app_content.dart`
   - `lib/src/presentation/screens/settings/settings_screen.dart`
   - `lib/src/presentation/screens/presets/presets_list_screen.dart`
   - `lib/src/presentation/screens/settings/components/*.dart`
4. If approach is still approved → invoke `superpowers:writing-plans` for step-by-step plan.
