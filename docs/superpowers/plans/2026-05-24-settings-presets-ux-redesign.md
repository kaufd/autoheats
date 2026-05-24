# Settings & Presets UX Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Merge preset configuration + preset listing into a single "Пресеты" tab, slim down "Настройки" to just theme + temperature-visibility, and reorder tabs to `Управление → Пресеты → Настройки`.

**Architecture:** Path A — editor binds to `Preset` records only; `ManualSettings` stays as the runtime layer untouched. New `PresetsTab` widget owns local state (`selectedUser`, `editingPresetId`, optional `draftSettings` for unsaved edits). Per-row `[✎]` (edit) and `[▶]` (apply) icons. Apply implicitly saves dirty edits on the same preset (E1). When a different preset becomes active mid-edit, the editor jumps silently (3-α). When no preset is active (or `[+ Новый пресет]` is pressed), the editor shows `ManualHeatSettings.defaultFor(userType)`.

**Tech Stack:** Flutter 3.6, flutter_bloc (Cubit), get_it DI, shared_preferences persistence, json_serializable models.

**Testing policy (user-approved):** No new automated tests for this iteration. Manual verification at the end of each phase. Existing tests (`flutter test`, `flutter analyze`) must remain green.

**Spec:** `docs/superpowers/specs/2026-05-24-settings-presets-ux-redesign-design.md`.

---

## File map (lock the decomposition before writing code)

**Create:**

- `lib/src/presentation/screens/presets/presets_tab.dart` — new screen (`StatefulWidget`), holds `selectedUser` / `editingPresetId` / `draftSettings`, composes `PresetEditor` + `PresetList`.
- `lib/src/presentation/screens/presets/components/preset_editor.dart` — left column: threshold + 3 level sliders + name input (when `isNewPresetDraft`) + `[Сохранить]` button.
- `lib/src/presentation/screens/presets/components/user_segment_toggle.dart` — top `[Driver | Passenger]` segmented control bound to parent's `selectedUser` state.

**Rewrite:**

- `lib/src/presentation/screens/presets/components/preset_list.dart` — new shape: vertical list filtered by `selectedUser`, each row has `[✎]` + `[▶]` icon buttons + name + active marker (★) + editing marker (▸) + delete icon, plus `[+ Новый пресет]` button below.

**Modify:**

- `lib/src/presentation/app_content.dart` — tab order, swap `PresetsListScreen` for `PresetsTab`, keep `_applyPreset` but wire it into `PresetsTab` callback.
- `lib/src/presentation/screens/settings/settings_screen.dart` — remove preset-config section, drop `ManualSettingsCubit.initialize()` call (moves into `PresetsTab.initState`).
- `lib/src/cubit/preset_cubit.dart` — add `updatePresetSettings(Preset, ManualHeatSettings)` method (calls existing `PresetService.savePreset(updated)` which already handles upsert by id).

**Delete (after migration):**

- `lib/src/presentation/screens/presets/presets_list_screen.dart` — absorbed into `PresetsTab`.
- `lib/src/presentation/screens/settings/components/presets_section.dart` — no longer used.
- `lib/src/presentation/screens/settings/components/presets_settings.dart` — no longer used.

**Keep / reuse:**

- `lib/src/presentation/screens/settings/components/auto_heat_level_slider.dart` — reused inside `PresetEditor` (import-only change of consumer).
- `lib/src/presentation/screens/settings/components/temperature_threshold_slider.dart` — same.
- `lib/src/presentation/screens/settings/components/manual_settings_section.dart` — reused as the slider body inside `PresetEditor` (or inlined; decide in Task 3 below).
- `lib/src/presentation/screens/settings/components/save_preset_dialog.dart` — reused for the `[+ Новый пресет]` flow.

**GRACE artifacts to update:**

- `docs/knowledge-graph.xml` — restructure `M-UI-PRESETS` / `M-UI-SETTINGS`.
- `docs/development-plan.xml` — new slice entry.
- `docs/verification-plan.xml` — refresh `V-M-UI-PRESETS` / `V-M-UI-SETTINGS`.
- `docs/operational-packets.xml` — verify no packet still references old surface.

---

## Task 1: Add `updatePresetSettings` to `PresetCubit`

**Files:**
- Modify: `lib/src/cubit/preset_cubit.dart`

- [ ] **Step 1: Add the method**

Add after `savePreset` (around line 117):

```dart
// START_CONTRACT: updatePresetSettings
//   PURPOSE: Persist new settings into an existing preset by id (keeps name/createdAt/heatMode/heatLevel).
//   INPUTS: { preset: Preset, settings: ManualHeatSettings }
//   OUTPUTS: { Future<void> }
//   SIDE_EFFECTS: SharedPreferences write через PresetService.savePreset upsert, reload list.
//   LINKS: M-PRESET, V-M-PRESET, FA-011
// END_CONTRACT: updatePresetSettings
Future<void> updatePresetSettings(
  Preset preset,
  ManualHeatSettings settings,
) async {
  // START_BLOCK_UPDATE_PRESET_SETTINGS
  try {
    final updated = preset.copyWith(settings: settings);
    await _presetService.savePreset(updated);
    await loadAllPresets();
  } catch (e) {
    emit(state.copyWith(error: e.toString()));
  }
  // END_BLOCK_UPDATE_PRESET_SETTINGS
}
```

- [ ] **Step 2: Update MODULE_MAP header**

Find `MODULE_MAP` section near top of the file and add a line after `savePreset`:

```dart
//   updatePresetSettings - upsert settings on an existing Preset by id; reload list
```

- [ ] **Step 3: Bump CHANGE_SUMMARY**

Replace the `LAST_CHANGE` line:

```dart
//   LAST_CHANGE: [v1.3.0 - Add updatePresetSettings for in-place editing of saved presets]
//   PREVIOUS_CHANGE: [v1.2.0 - Phase-4 Slice-9: selected preset persists per user]
```

- [ ] **Step 4: Verify analyzer clean**

```bash
flutter analyze lib/src/cubit/preset_cubit.dart
```

Expected: `No issues found!`.

- [ ] **Step 5: Verify existing tests still pass**

```bash
flutter test
```

Expected: all green.

---

## Task 2: Create `UserSegmentToggle` widget

**Files:**
- Create: `lib/src/presentation/screens/presets/components/user_segment_toggle.dart`

- [ ] **Step 1: Write the widget**

```dart
import 'package:autoheat/src/app_enums.dart';
import 'package:flutter/material.dart';

class UserSegmentToggle extends StatelessWidget {
  final UserType selectedUser;
  final ValueChanged<UserType> onChanged;

  const UserSegmentToggle({
    super.key,
    required this.selectedUser,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    return SegmentedButton<UserType>(
      segments: const [
        ButtonSegment(value: UserType.driver, label: Text('Водитель')),
        ButtonSegment(value: UserType.passenger, label: Text('Пассажир')),
      ],
      selected: {selectedUser},
      showSelectedIcon: false,
      onSelectionChanged: (set) => onChanged(set.first),
    );
  }
}
```

- [ ] **Step 2: Verify**

```bash
flutter analyze lib/src/presentation/screens/presets/components/user_segment_toggle.dart
```

Expected: `No issues found!`.

---

## Task 3: Create `PresetEditor` widget

**Files:**
- Create: `lib/src/presentation/screens/presets/components/preset_editor.dart`

**Design notes (resolves spec Open Questions #1, #2):**

- Name input is **inline** at the top of the editor (only visible when `isNewPresetDraft == true`). Avoids modal context-switch.
- "Empty / no preset" state shows `ManualHeatSettings.defaultFor(userType)` values, not literal zeros. Same UI as edit-mode but with a faint subtitle "Создайте новый пресет" above the sliders.
- `[Сохранить]` button under the sliders. Disabled when `isNewPresetDraft && name.trim().isEmpty`.

- [ ] **Step 1: Write the widget skeleton**

```dart
// FILE: lib/src/presentation/screens/presets/components/preset_editor.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Editor pane для одного пресета: name (new draft only), threshold + level sliders, Save button.
//   SCOPE: read-only props + onChange callbacks, no Bloc access, parent owns state.
//   DEPENDS: M-UI-PRESETS, M-MANUAL-SETTINGS, M-ENUMS, M-THEME
//   LINKS: M-UI-PRESETS, DF-PRESET-APPLY, FA-001
//   ROLE: RUNTIME
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   PresetEditor - StatelessWidget: sliders + optional name field + save button
//   _buildHeader - title or name TextField depending on isNewPresetDraft
//   _buildBody - ManualSettingsSection delegating slider callbacks
//   _buildSaveButton - styled TextButton wired to onSave (disabled when invalid)
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.0.0 - Initial PresetEditor for merged Presets tab]
// END_CHANGE_SUMMARY

import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/extensions/context_extensions.dart';
import 'package:autoheat/src/models/manual_settings.dart';
import 'package:autoheat/src/presentation/screens/settings/components/manual_settings_section.dart';
import 'package:flutter/material.dart';

class PresetEditor extends StatelessWidget {
  final UserType userType;
  final ManualHeatSettings settings;
  final String? presetName; // null when editing default/empty state
  final bool isActive; // true when this preset is the user's selectedPreset
  final bool isNewPresetDraft;
  final TextEditingController? nameController;
  final bool isSaveEnabled;
  final ValueChanged<AutoHeatLevel> onAutoHeatLevelTap; // placeholder; real signature below
  final void Function(AutoHeatLevel, int) onAutoHeatLevelChanged;
  final ValueChanged<double> onTemperatureThresholdChanged;
  final VoidCallback onSave;

  const PresetEditor({
    super.key,
    required this.userType,
    required this.settings,
    required this.presetName,
    required this.isActive,
    required this.isNewPresetDraft,
    required this.nameController,
    required this.isSaveEnabled,
    required this.onAutoHeatLevelTap,
    required this.onAutoHeatLevelChanged,
    required this.onTemperatureThresholdChanged,
    required this.onSave,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _buildHeader(context),
          const SizedBox(height: 8),
          ManualSettingsSection(
            userType: userType,
            settings: settings,
            onAutoHeatLevelChanged: onAutoHeatLevelChanged,
            onTemperatureThresholdChanged: onTemperatureThresholdChanged,
          ),
          const SizedBox(height: 16),
          _buildSaveButton(context),
        ],
      ),
    );
  }

  Widget _buildHeader(BuildContext context) {
    if (isNewPresetDraft && nameController != null) {
      return TextField(
        controller: nameController,
        style: TextStyle(color: context.themeColors.textBody),
        decoration: InputDecoration(
          hintText: 'Название нового пресета',
          hintStyle:
              TextStyle(color: context.themeColors.textBody.withValues(alpha: 0.5)),
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(8),
            borderSide: BorderSide(
              color: context.themeColors.primary.withValues(alpha: 0.3),
            ),
          ),
        ),
      );
    }

    final title = presetName ?? 'Создайте новый пресет';
    return Row(
      children: [
        if (isActive)
          Padding(
            padding: const EdgeInsets.only(right: 6),
            child: Icon(Icons.star, size: 18, color: context.themeColors.primary),
          ),
        Expanded(
          child: Text(
            title,
            style: context.textStyle.textSettings,
            overflow: TextOverflow.ellipsis,
          ),
        ),
      ],
    );
  }

  Widget _buildSaveButton(BuildContext context) {
    return TextButton(
      onPressed: isSaveEnabled ? onSave : null,
      style: ButtonStyle(
        foregroundColor:
            WidgetStatePropertyAll(context.themeColors.textButtonSelected),
        backgroundColor: WidgetStatePropertyAll(context.themeColors.primary),
      ),
      child: const Text('Сохранить'),
    );
  }
}
```

- [ ] **Step 2: Remove the placeholder `onAutoHeatLevelTap` parameter**

The skeleton above has an unused `onAutoHeatLevelTap` callback. Delete it both from the field declaration and the constructor `required` list. Final constructor should match the actual usages in `build`.

- [ ] **Step 3: Verify analyzer clean**

```bash
flutter analyze lib/src/presentation/screens/presets/components/preset_editor.dart
```

Expected: `No issues found!`.

---

## Task 4: Rewrite `PresetList`

**Files:**
- Modify (rewrite): `lib/src/presentation/screens/presets/components/preset_list.dart`

- [ ] **Step 1: Replace the file contents**

```dart
import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/config/color_constants.dart';
import 'package:autoheat/src/models/preset.dart';
import 'package:autoheat/src/extensions/context_extensions.dart';
import 'package:autoheat/src/presentation/ui/custom_alert_dialog.dart';
import 'package:flutter/material.dart';

class PresetList extends StatelessWidget {
  final List<Preset> presets;
  final UserType selectedUser;
  final String? activePresetId;
  final String? editingPresetId;
  final ValueChanged<Preset> onEdit;
  final ValueChanged<Preset> onApply;
  final ValueChanged<Preset> onDelete;
  final VoidCallback onNewPreset;

  const PresetList({
    super.key,
    required this.presets,
    required this.selectedUser,
    required this.activePresetId,
    required this.editingPresetId,
    required this.onEdit,
    required this.onApply,
    required this.onDelete,
    required this.onNewPreset,
  });

  @override
  Widget build(BuildContext context) {
    final userPresets =
        presets.where((p) => p.userType == selectedUser).toList();

    return Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Expanded(
            child: userPresets.isEmpty
                ? _buildEmptyState(context)
                : ListView.separated(
                    itemCount: userPresets.length,
                    separatorBuilder: (_, __) => const SizedBox(height: 8),
                    itemBuilder: (_, index) =>
                        _buildRow(context, userPresets[index]),
                  ),
          ),
          const SizedBox(height: 12),
          _buildNewPresetButton(context),
        ],
      ),
    );
  }

  Widget _buildEmptyState(BuildContext context) {
    return Center(
      child: Text(
        'Пока нет сохранённых пресетов',
        style: context.textStyle.paragraph2.copyWith(
          color: context.themeColors.textMuted,
        ),
      ),
    );
  }

  Widget _buildRow(BuildContext context, Preset preset) {
    final isActive = preset.id == activePresetId;
    final isEditing = preset.id == editingPresetId;

    return Material(
      color: isEditing
          ? context.themeColors.primary.withValues(alpha: 0.18)
          : ColorConstants.systemWhite.withValues(alpha: 0.05),
      borderRadius: BorderRadius.circular(20),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(20),
          border: Border.all(
            color: isActive
                ? context.themeColors.primary
                : ColorConstants.systemWhite.withValues(alpha: 0.1),
            width: isActive ? 2 : 1,
          ),
        ),
        child: Row(
          children: [
            if (isActive)
              Padding(
                padding: const EdgeInsets.only(right: 6),
                child: Icon(Icons.star,
                    size: 18, color: context.themeColors.primary),
              ),
            Expanded(
              child: Text(
                preset.name,
                style: context.textStyle.paragraph1,
                overflow: TextOverflow.ellipsis,
              ),
            ),
            IconButton(
              onPressed: () => onEdit(preset),
              icon: const Icon(Icons.edit),
              tooltip: 'Редактировать',
              iconSize: 22,
              constraints: const BoxConstraints(minWidth: 44, minHeight: 44),
            ),
            IconButton(
              onPressed: () => onApply(preset),
              icon: const Icon(Icons.play_arrow),
              tooltip: 'Применить',
              iconSize: 24,
              constraints: const BoxConstraints(minWidth: 44, minHeight: 44),
            ),
            IconButton(
              onPressed: () => _confirmDelete(context, preset),
              icon: Icon(Icons.delete_outline, color: ColorConstants.error),
              tooltip: 'Удалить',
              iconSize: 22,
              constraints: const BoxConstraints(minWidth: 44, minHeight: 44),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildNewPresetButton(BuildContext context) {
    return OutlinedButton.icon(
      onPressed: onNewPreset,
      icon: const Icon(Icons.add),
      label: const Text('Новый пресет'),
      style: OutlinedButton.styleFrom(
        foregroundColor: context.themeColors.primary,
        side: BorderSide(color: context.themeColors.primary, width: 1),
      ),
    );
  }

  void _confirmDelete(BuildContext context, Preset preset) {
    showDialog(
      context: context,
      builder: (_) => CustomAlertDialog(
        content: Padding(
          padding: const EdgeInsets.only(bottom: 32),
          child: Text(
            'Удалить пресет "${preset.name}"?',
            textAlign: TextAlign.center,
            style: context.textStyle.paragraph1,
          ),
        ),
        confirmText: 'Удалить',
        onConfirm: () {
          Navigator.of(context).pop();
          onDelete(preset);
        },
      ),
    );
  }
}
```

- [ ] **Step 2: Verify analyzer clean**

```bash
flutter analyze lib/src/presentation/screens/presets/components/preset_list.dart
```

Expected: `No issues found!`.

---

## Task 5: Create `PresetsTab` (orchestrator)

**Files:**
- Create: `lib/src/presentation/screens/presets/presets_tab.dart`

This file owns ALL the state for the new tab. It is responsible for:
- Loading `ManualSettingsCubit` on init (moved from `SettingsScreen`).
- Loading `PresetCubit` list on init (moved from `PresetsListScreen`).
- `selectedUser` toggle.
- `editingPresetId` / `draftSettings` / `nameController` for the editor.
- Wiring `[✎]`, `[▶]`, `[Сохранить]`, `[+ Новый пресет]`, delete actions.
- Calling the `onPresetApplied` prop (parent in `AppContent` does the actual HVAC apply).

- [ ] **Step 1: Write the file**

```dart
// FILE: lib/src/presentation/screens/presets/presets_tab.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Merged Presets tab — Driver/Passenger toggle + editor + list + new-preset flow.
//   SCOPE: local state (selectedUser, editingPresetId, draftSettings, nameController),
//          wires PresetEditor + PresetList + UserSegmentToggle, delegates apply to parent.
//   DEPENDS: M-UI-PRESETS, M-PRESET, M-MANUAL-SETTINGS, M-MODE, M-ENUMS, M-THEME
//   LINKS: M-UI-PRESETS, V-M-UI-PRESETS, DF-PRESET-APPLY, FA-001, FA-011
//   ROLE: RUNTIME
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   PresetsTab - StatefulWidget с onPresetApplied callback
//   _PresetsTabState.initState - load PresetCubit + ManualSettingsCubit
//   _PresetsTabState._activePresetFor - selectedPresets[user]?.id
//   _PresetsTabState._editorSettings - draftSettings ?? active preset settings ?? defaults
//   _PresetsTabState._editorPresetName - active preset name or null when default state
//   _PresetsTabState._onEdit - clone preset settings into draft, set editingPresetId
//   _PresetsTabState._onApply - save dirty edits if same preset, then onPresetApplied
//   _PresetsTabState._onSave - upsert preset via PresetCubit.updatePresetSettings or savePreset
//   _PresetsTabState._onNewPreset - open empty/default draft with name field
//   _PresetsTabState._handleActivePresetJump - case 3-α silent jump
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.0.0 - Initial merged Presets tab]
// END_CHANGE_SUMMARY

import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/cubit/manual_settings_cubit.dart';
import 'package:autoheat/src/cubit/mode_cubit.dart';
import 'package:autoheat/src/cubit/preset_cubit.dart';
import 'package:autoheat/src/extensions/context_extensions.dart';
import 'package:autoheat/src/models/manual_settings.dart';
import 'package:autoheat/src/models/preset.dart';
import 'package:autoheat/src/presentation/screens/presets/components/preset_editor.dart';
import 'package:autoheat/src/presentation/screens/presets/components/preset_list.dart';
import 'package:autoheat/src/presentation/screens/presets/components/user_segment_toggle.dart';
import 'package:autoheat/src/presentation/ui/error_block.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

class PresetsTab extends StatefulWidget {
  final void Function(Preset preset) onPresetApplied;

  const PresetsTab({super.key, required this.onPresetApplied});

  @override
  State<PresetsTab> createState() => _PresetsTabState();
}

class _PresetsTabState extends State<PresetsTab> {
  UserType _selectedUser = UserType.driver;
  String? _editingPresetId;
  ManualHeatSettings? _draftSettings;
  bool _isNewPresetDraft = false;
  TextEditingController? _nameController;

  @override
  void initState() {
    super.initState();
    context.read<ManualSettingsCubit>().initialize();
    context.read<PresetCubit>().loadAllPresets();
  }

  @override
  void dispose() {
    _nameController?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.only(bottom: 16),
          child: Center(
            child: UserSegmentToggle(
              selectedUser: _selectedUser,
              onChanged: _onUserChanged,
            ),
          ),
        ),
        Expanded(
          child: BlocConsumer<PresetCubit, PresetState>(
            listener: _handleActivePresetJump,
            builder: (context, presetState) {
              if (presetState.isLoading) {
                return Center(
                  child: CircularProgressIndicator(
                    color: context.themeColors.primary,
                  ),
                );
              }

              if (presetState.error != null) {
                return const ErrorBlock(
                    message: 'Ошибка загрузки пресетов');
              }

              return IntrinsicHeight(
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Expanded(child: _buildEditor(context, presetState)),
                    Container(
                      width: 2,
                      color:
                          context.themeColors.primary.withValues(alpha: 0.27),
                    ),
                    Expanded(child: _buildList(context, presetState)),
                  ],
                ),
              );
            },
          ),
        ),
      ],
    );
  }

  Widget _buildEditor(BuildContext context, PresetState presetState) {
    final activePreset = presetState.selectedPresets[_selectedUser];
    final editingPreset = _editingPresetId == null
        ? null
        : presetState.presets.firstWhere(
            (p) => p.id == _editingPresetId,
            orElse: () => activePreset ??
                Preset(
                  id: '',
                  name: '',
                  userType: _selectedUser,
                  settings: ManualHeatSettings.defaultFor(_selectedUser),
                  createdAt: DateTime.now(),
                ),
          );

    final fallbackSettings =
        editingPreset?.settings ??
            activePreset?.settings ??
            ManualHeatSettings.defaultFor(_selectedUser);

    final displayedSettings = _draftSettings ?? fallbackSettings;

    final displayedName = _isNewPresetDraft
        ? null
        : editingPreset?.name ?? activePreset?.name;

    final isActiveShown = !_isNewPresetDraft &&
        editingPreset == null &&
        activePreset != null;

    final isSaveEnabled = _isNewPresetDraft
        ? (_nameController?.text.trim().isNotEmpty ?? false)
        : true;

    return PresetEditor(
      userType: _selectedUser,
      settings: displayedSettings,
      presetName: displayedName,
      isActive: isActiveShown,
      isNewPresetDraft: _isNewPresetDraft,
      nameController: _nameController,
      isSaveEnabled: isSaveEnabled,
      onAutoHeatLevelChanged: _onAutoHeatLevelChanged,
      onTemperatureThresholdChanged: _onTemperatureThresholdChanged,
      onSave: () => _onSave(presetState),
    );
  }

  Widget _buildList(BuildContext context, PresetState presetState) {
    final activePresetId = presetState.selectedPresets[_selectedUser]?.id;
    return PresetList(
      presets: presetState.presets,
      selectedUser: _selectedUser,
      activePresetId: activePresetId,
      editingPresetId: _editingPresetId,
      onEdit: _onEdit,
      onApply: _onApply,
      onDelete: _onDelete,
      onNewPreset: _onNewPreset,
    );
  }

  void _onUserChanged(UserType user) {
    setState(() {
      _selectedUser = user;
      _editingPresetId = null;
      _draftSettings = null;
      _isNewPresetDraft = false;
      _nameController?.dispose();
      _nameController = null;
    });
  }

  void _onEdit(Preset preset) {
    setState(() {
      _editingPresetId = preset.id;
      _draftSettings = preset.settings;
      _isNewPresetDraft = false;
      _nameController?.dispose();
      _nameController = null;
    });
  }

  Future<void> _onApply(Preset preset) async {
    if (_editingPresetId == preset.id && _draftSettings != null) {
      await context
          .read<PresetCubit>()
          .updatePresetSettings(preset, _draftSettings!);
    }
    widget.onPresetApplied(preset);
  }

  Future<void> _onDelete(Preset preset) async {
    await context.read<PresetCubit>().deletePreset(preset.id, preset.userType);
    if (!mounted) return;
    if (_editingPresetId == preset.id) {
      setState(() {
        _editingPresetId = null;
        _draftSettings = null;
      });
    }
  }

  void _onNewPreset() {
    setState(() {
      _editingPresetId = null;
      _draftSettings = ManualHeatSettings.defaultFor(_selectedUser);
      _isNewPresetDraft = true;
      _nameController?.dispose();
      _nameController = TextEditingController()
        ..addListener(() => setState(() {})); // re-evaluate save-enabled
    });
  }

  Future<void> _onSave(PresetState presetState) async {
    final draft = _draftSettings;
    if (draft == null) return;

    if (_isNewPresetDraft) {
      final name = _nameController?.text.trim() ?? '';
      if (name.isEmpty) return;

      final modeCubit = context.read<ModeCubit>();
      final heatMode = HeatModeExtension.fromString(
          modeCubit.getModeByUser(_selectedUser));
      final heatLevel = modeCubit.getHeatLevelByUser(_selectedUser);

      await context.read<PresetCubit>().savePreset(
            name: name,
            userType: _selectedUser,
            settings: draft,
            heatMode: heatMode,
            heatLevel: heatLevel,
          );

      if (!mounted) return;
      setState(() {
        _isNewPresetDraft = false;
        _nameController?.dispose();
        _nameController = null;
        _draftSettings = null;
        _editingPresetId = null;
      });
      return;
    }

    final id = _editingPresetId;
    if (id == null) return;
    final preset = presetState.presets.firstWhere(
      (p) => p.id == id,
      orElse: () => presetState.selectedPresets[_selectedUser]!,
    );

    await context.read<PresetCubit>().updatePresetSettings(preset, draft);
    if (!mounted) return;
    setState(() {
      _draftSettings = null;
    });
  }

  void _onAutoHeatLevelChanged(AutoHeatLevel autoHeatLevel, int duration) {
    setState(() {
      final source = _draftSettings ??
          _currentEditorSettings(context.read<PresetCubit>().state);
      _draftSettings = source.copyWith(
        autoHeatLevels: source.autoHeatLevels
            .map((level) => level == autoHeatLevel
                ? level.copyWith(duration: duration)
                : level)
            .toList(),
      );
    });
  }

  void _onTemperatureThresholdChanged(double threshold) {
    setState(() {
      final source = _draftSettings ??
          _currentEditorSettings(context.read<PresetCubit>().state);
      _draftSettings = source.copyWith(temperatureThreshold: threshold);
    });
  }

  ManualHeatSettings _currentEditorSettings(PresetState presetState) {
    if (_editingPresetId != null) {
      final preset = presetState.presets.firstWhere(
        (p) => p.id == _editingPresetId,
        orElse: () => presetState.selectedPresets[_selectedUser]!,
      );
      return preset.settings;
    }
    final active = presetState.selectedPresets[_selectedUser];
    return active?.settings ?? ManualHeatSettings.defaultFor(_selectedUser);
  }

  void _handleActivePresetJump(BuildContext context, PresetState state) {
    final newActive = state.selectedPresets[_selectedUser];
    if (newActive == null) return;

    if (_isNewPresetDraft) return; // user is mid-create, don't blow it away

    if (_editingPresetId != null && _editingPresetId != newActive.id) {
      // case 3-α: silent jump
      setState(() {
        _editingPresetId = null;
        _draftSettings = null;
      });
    }
  }
}
```

- [ ] **Step 2: Verify analyzer clean**

```bash
flutter analyze lib/src/presentation/screens/presets/presets_tab.dart
```

Expected: `No issues found!`.

---

## Task 6: Wire `PresetsTab` into `AppContent` and reorder tabs

**Files:**
- Modify: `lib/src/presentation/app_content.dart`

- [ ] **Step 1: Replace `PresetsListScreen` import**

Change:

```dart
import 'package:autoheat/src/presentation/screens/presets/presets_list_screen.dart';
```

to:

```dart
import 'package:autoheat/src/presentation/screens/presets/presets_tab.dart';
```

- [ ] **Step 2: Reorder tab buttons in `AppBar`**

Find the `_buildTabButton` calls (currently lines 99–101) and reorder to:

```dart
_buildTabButton('Управление', 0, _selectTab),
_buildTabButton('Пресеты', 1, _selectTab),
_buildTabButton('Настройки', 2, _selectTab),
```

- [ ] **Step 3: Reorder `TabBarView` children**

Replace the `children:` block under `TabBarView` (currently lines 118–126):

```dart
children: [
  HeatScreen(),
  PresetsTab(
    onPresetApplied: (preset) {
      _applyPreset(preset);
    },
  ),
  SettingsScreen(),
],
```

- [ ] **Step 4: Bump CHANGE_SUMMARY**

Replace the `LAST_CHANGE` line in the file header:

```dart
//   LAST_CHANGE: [v1.2.0 - Settings/Presets UX redesign: tab order Управление→Пресеты→Настройки, merged PresetsTab]
//   PREVIOUS_CHANGE: [v1.1.0 - Phase-4 Slice-1: preset apply delegates to ModeCubit.applyPreset]
```

- [ ] **Step 5: Verify analyzer clean**

```bash
flutter analyze lib/src/presentation/app_content.dart
```

Expected: `No issues found!`.

---

## Task 7: Slim down `SettingsScreen`

**Files:**
- Modify: `lib/src/presentation/screens/settings/settings_screen.dart`

- [ ] **Step 1: Replace the file**

```dart
// FILE: lib/src/presentation/screens/settings/settings_screen.dart
// VERSION: 1.2.0
// START_MODULE_CONTRACT
//   PURPOSE: Глобальные настройки приложения: тема и видимость температуры салона.
//   SCOPE: ThemeSelector, CustomSwitch для cabin-temperature visibility. Все настройки пресетов
//          переехали в PresetsTab; этот экран теперь slim и не использует ManualSettingsCubit.
//   DEPENDS: M-UI-SETTINGS, M-SETTINGS, M-THEME
//   LINKS: M-UI-SETTINGS, V-M-UI-SETTINGS, FA-009
//   ROLE: RUNTIME
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   SettingsScreen - StatelessWidget: theme row + cabin-temperature visibility row
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.2.0 - Slim settings: preset config moved to PresetsTab, ManualSettingsCubit init removed]
//   PREVIOUS_CHANGE: [v1.1.0 - Phase-4 Slice-6: GRACE contract and head-unit layout smoke coverage]
// END_CHANGE_SUMMARY

import 'package:autoheat/src/cubit/settings_cubit.dart';
import 'package:autoheat/src/extensions/context_extensions.dart';
import 'package:autoheat/src/presentation/screens/settings/components/theme_selector.dart';
import 'package:autoheat/src/presentation/ui/custom_switch.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.start,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'Тема приложения: ',
                style: context.textStyle.textSettings,
              ),
              const Expanded(child: ThemeSelector()),
            ],
          ),
          const SizedBox(height: 20),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'Показывать температуру в салоне: ',
                style: context.textStyle.textSettings,
              ),
              BlocBuilder<SettingsCubit, SettingsState>(
                builder: (context, settingsState) {
                  return CustomSwitch(
                    value: settingsState.showCabinTemperature,
                    onChanged: (value) async {
                      await context
                          .read<SettingsCubit>()
                          .setCabinTemperatureVisibility(value);
                    },
                  );
                },
              ),
            ],
          ),
        ],
      ),
    );
  }
}
```

- [ ] **Step 2: Verify analyzer clean**

```bash
flutter analyze lib/src/presentation/screens/settings/settings_screen.dart
```

Expected: `No issues found!`.

---

## Task 8: Delete obsolete files

**Files:**
- Delete: `lib/src/presentation/screens/presets/presets_list_screen.dart`
- Delete: `lib/src/presentation/screens/settings/components/presets_section.dart`
- Delete: `lib/src/presentation/screens/settings/components/presets_settings.dart`

- [ ] **Step 1: Verify no remaining imports**

```bash
grep -rn "presets_list_screen\|presets_section\|presets_settings" lib/ test/
```

Expected: matches only inside files we're about to delete (if any), or no matches at all. If any other file still imports these, fix before deleting.

- [ ] **Step 2: Delete the files**

```bash
rm lib/src/presentation/screens/presets/presets_list_screen.dart
rm lib/src/presentation/screens/settings/components/presets_section.dart
rm lib/src/presentation/screens/settings/components/presets_settings.dart
```

- [ ] **Step 3: Verify nothing else broke**

```bash
flutter analyze
```

Expected: `No issues found!`.

---

## Task 9: Commit baseline

- [ ] **Step 1: Stage and commit**

```bash
git add lib/src/cubit/preset_cubit.dart \
        lib/src/presentation/screens/presets/ \
        lib/src/presentation/app_content.dart \
        lib/src/presentation/screens/settings/settings_screen.dart
git rm lib/src/presentation/screens/presets/presets_list_screen.dart \
       lib/src/presentation/screens/settings/components/presets_section.dart \
       lib/src/presentation/screens/settings/components/presets_settings.dart
git commit -m "Merge preset workflow into single Presets tab, slim Settings tab"
```

(Pre-commit hooks may run — let them.)

---

## Task 10: Manual verification on the running app

The app is already running on UNI-S; hot-reload should pick up most changes. If hot reload misbehaves, hot-restart (`R` in the `flutter run` terminal).

- [ ] **Step 1: Verify tab order**

Open the app → check AppBar reads `Управление | Пресеты | Настройки` (in that order). Both buttons clickable.

- [ ] **Step 2: Verify Settings tab is slim and toggle is above the fold**

Navigate to `Настройки`. Confirm both `Тема приложения` and `Показывать температуру в салоне` rows are visible without scrolling. Toggle temperature visibility on/off twice — confirm the HUD on the `Управление` tab updates.

- [ ] **Step 3: Verify Presets tab — empty state**

Clear shared preferences if needed (uninstall/reinstall or use a fresh emulator). Navigate to `Пресеты`. Confirm:
- `[Водитель] | Пассажир` segmented control at the top.
- Editor on the left shows default slider values (durations 2/5/10, threshold from constants) and title "Создайте новый пресет".
- Right side shows "Пока нет сохранённых пресетов".
- `[Новый пресет]` button visible at the bottom.

- [ ] **Step 4: Verify creating a new preset**

Click `[Новый пресет]`. Name field appears at the top of the editor. `Сохранить` button is disabled. Type "Test 1". Move a slider. Click `Сохранить`. Confirm:
- New preset row appears on the right.
- Editor reverts to default/active state.

- [ ] **Step 5: Verify edit + save existing preset**

Click `[✎]` on "Test 1". Editor shows that preset's data. Move the threshold slider. Click `Сохранить`. Re-open the app (hot restart). Click `[✎]` on "Test 1" again — confirm changes persisted.

- [ ] **Step 6: Verify apply implicit save (E1)**

Click `[✎]` on "Test 1". Move a slider. Without clicking Save, click `[▶]` on "Test 1". Confirm:
- Star (★) appears next to "Test 1" indicating active.
- Re-open and `[✎]` again — the slider change persisted (saved-then-applied).
- Heat-screen indicators reflect the preset (if a temperature is being injected).

- [ ] **Step 7: Verify silent jump (case 3-α)**

Create a second preset "Test 2". Click `[✎]` on "Test 1", move sliders (don't save). Click `[▶]` on "Test 2". Confirm editor immediately switches to "Test 2"'s saved data, no warning, no dialog. Re-open and `[✎]` on "Test 1" — confirm earlier unsaved changes are lost.

- [ ] **Step 8: Verify driver/passenger isolation**

Switch to `Пассажир`. Confirm list is empty (no driver presets shown). Create a passenger preset. Switch back to `Водитель`. Confirm driver presets still present and passenger one is hidden.

- [ ] **Step 9: Verify delete**

Click the delete icon on any preset, confirm dialog. Preset disappears. If it was active (★), star disappears.

- [ ] **Step 10: Confirm `flutter analyze` and existing tests still green**

```bash
flutter analyze
flutter test
```

Expected: no issues, all existing tests pass.

---

## Task 11: Update GRACE artifacts

**Files:**
- Modify: `docs/knowledge-graph.xml`
- Modify: `docs/development-plan.xml`
- Modify: `docs/verification-plan.xml`
- Verify: `docs/operational-packets.xml`

GRACE artifact edits use surgical XML edits, not full rewrites. The structure varies by project age; consult each file before editing.

- [ ] **Step 1: Inspect `knowledge-graph.xml` for `M-UI-PRESETS` and `M-UI-SETTINGS`**

```bash
grep -n "M-UI-PRESETS\|M-UI-SETTINGS" docs/knowledge-graph.xml
```

- [ ] **Step 2: Update `M-UI-PRESETS` entry**

Find the `<M-UI-PRESETS …>` element. Update `depends` to include `M-MANUAL-SETTINGS` and `M-MODE` (the merged tab now owns preset editing too):

```xml
<depends>M-PRESET, M-MANUAL-SETTINGS, M-MODE, M-ENUMS, M-THEME</depends>
```

Update its `MODULE_MAP` to mention `PresetsTab`, `PresetEditor`, `PresetList`, `UserSegmentToggle`.

- [ ] **Step 3: Update `M-UI-SETTINGS` entry**

Find the `<M-UI-SETTINGS …>` element. Update `depends` to remove `M-MANUAL-SETTINGS` (no longer used by SettingsScreen) and `M-PRESET`:

```xml
<depends>M-SETTINGS, M-THEME</depends>
```

Update its `MODULE_MAP` to reflect the slim 2-row layout.

- [ ] **Step 4: Update `CrossLink` block**

Add (or update) these CrossLinks:

```xml
<CrossLink from="M-UI-PRESETS" to="M-PRESET" relation="lists-and-edits" />
<CrossLink from="M-UI-PRESETS" to="M-MANUAL-SETTINGS" relation="edits-settings" />
<CrossLink from="M-UI-PRESETS" to="M-MODE" relation="applies-preset" />
```

Remove the old `M-UI-SETTINGS → M-MANUAL-SETTINGS` and `M-UI-SETTINGS → M-PRESET` links if present.

- [ ] **Step 5: Update `development-plan.xml`**

Find the latest phase/slice block and add a new slice entry "Settings & Presets UX Redesign" marked `STATUS="implemented"`. Mirror the formatting style used by neighboring slices (refer to a recent existing entry for the exact tag shape — different projects use `<slice>`, `<task>`, or other custom XML).

- [ ] **Step 6: Update `verification-plan.xml`**

Find `V-M-UI-PRESETS` and `V-M-UI-SETTINGS`. Refresh their scenarios to describe what the merged tab and slim settings must guarantee. Example scenarios for `V-M-UI-PRESETS`:

- "Tab renders Driver/Passenger toggle + editor + list."
- "Clicking edit on a preset row loads its settings into the editor."
- "Clicking apply on a preset row triggers `onPresetApplied` callback and updates active marker."
- "Clicking apply while editor is dirty for the same preset saves before applying (E1)."
- "When active preset changes mid-edit on a different preset, editor jumps silently (3-α)."
- "Empty state shows default settings via `ManualHeatSettings.defaultFor`."

Example scenario for `V-M-UI-SETTINGS`:

- "Both theme and cabin-temperature-visibility rows render above the fold on `Size(1920, 720)` without scroll."

- [ ] **Step 7: Verify `operational-packets.xml`**

```bash
grep -n "PresetsListScreen\|presets_section\|presets_settings" docs/operational-packets.xml
```

If any matches exist, replace references to old module surface with the new merged `PresetsTab` surface, or remove obsolete entries.

- [ ] **Step 8: Optional GRACE lint (skip if grace CLI not configured for Dart)**

```bash
grace lint || echo "grace CLI not available — skipping (per MEMORY: grace-cli does not support .dart)"
```

---

## Task 12: Final commit + cleanup

- [ ] **Step 1: Stage GRACE changes**

```bash
git add docs/knowledge-graph.xml docs/development-plan.xml docs/verification-plan.xml
git commit -m "Update GRACE artifacts for merged Presets tab + slim Settings"
```

- [ ] **Step 2: Mark spec status complete**

In `docs/superpowers/specs/2026-05-24-settings-presets-ux-redesign-design.md`, change the status header:

```
> **Status:** Implemented 2026-05-24 (plan `docs/superpowers/plans/2026-05-24-settings-presets-ux-redesign.md`).
```

Then:

```bash
git add docs/superpowers/specs/2026-05-24-settings-presets-ux-redesign-design.md
git commit -m "Mark Settings/Presets UX redesign spec as implemented"
```

---

## Open follow-ups (out of this plan)

- Untracked test `test/widget/heat_screen_manual_level_selector_test.dart` was failing before this work on `find.text('off')`. Either fix or delete in a separate change.
- The earlier `ModeToggler` layout work (Visibility(maintainSize) approach) is independent and already shipped.
- Dev-stub `HvacGateway` (separate spec at `docs/superpowers/specs/2026-05-24-dev-stub-hvac-gateway-design.md`) remains open and unrelated.

---

## Self-review notes

- **Spec coverage:** Decisions 1–9 from spec each map to at least one task (1→Task 6, 2→Tasks 5+6+7, 3→Tasks 3/4/5, 4→Task 5 `_currentEditorSettings`/`_buildEditor`, 5→Task 5 `_onApply`, 6→Task 5 `_handleActivePresetJump`, 7→Task 5 `_onNewPreset`+Task 3 inline name field, 8→Task 7, 9→implicit, no HeatScreen task). Path A locked: `ManualSettings` untouched, only the cubit method `updatePresetSettings` added (Task 1) — does not change runtime layer.
- **Placeholder scan:** No `TBD`/`TODO`. Every step has the actual code or command. The "PresetEditor" header field `onAutoHeatLevelTap` was a placeholder slip — Task 3 Step 2 explicitly removes it.
- **Type consistency:** `PresetCubit.updatePresetSettings(Preset, ManualHeatSettings)` used identically in Task 1, Task 5 `_onApply`, Task 5 `_onSave`. `PresetList` props (`presets`, `selectedUser`, `activePresetId`, `editingPresetId`, callbacks) match Task 5 call site. `PresetEditor` props match Task 5 call site after Step 2 placeholder removal.
- **No new tests** per user instruction; manual verification consolidated in Task 10.
