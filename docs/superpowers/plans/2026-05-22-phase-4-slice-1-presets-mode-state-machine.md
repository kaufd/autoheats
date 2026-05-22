# Phase-4 Slice 1 Presets + Mode State Machine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix FA-001/FA-011 and the adjacent FA-004/FA-010 mode-transition bugs so applying a preset becomes a real `ModeCubit -> HvacService` operation, and manual/non-manual transitions have deterministic HVAC effects.

**Architecture:** Keep the existing `Preset` model as the user-facing saved configuration, but extend it with explicit runtime state: `heatMode` and `heatLevel`. `ModeCubit` becomes the owner of applying a preset to runtime/persistence/HVAC through a new `applyPreset(Preset)` method. UI screens keep their current structure: settings still edits/saves preset settings, presets screen calls `AppContent._applyPreset`, and `_applyPreset` delegates runtime changes to `ModeCubit`.

**Tech Stack:** Flutter/Dart, `flutter_bloc`, `shared_preferences`, `json_serializable`, `flutter_test`, existing `FakeHvacService` and `LoggerTestSink`. Follow GRACE contracts/semantic anchors and TDD.

---

## Scope decisions

1. **Preset runtime semantics:** A saved preset stores settings plus the user's current `HeatMode` and `heatLevel` for the preset's `UserType` at save time.
2. **Applying a preset:** Applying a preset updates manual settings for that user, persists selected mode/level, sends `HvacService.setSeatHeatLevel(userType, heatLevel)`, updates `ModesState`, and records last-used metadata.
3. **Legacy preset migration:** Old JSON presets without `heatMode`/`heatLevel` load as `HeatMode.presets` and `heatLevel=0`. This avoids parse failures and makes old presets safe.
4. **Manual mode via segmented control:** explicit `setMode(user, manual)` stops auto and sends HVAC level `0` only when current state level is non-zero.
5. **Tap seat from non-manual:** `toggleHeatLevel` switches to manual and applies level `1` sequentially without a parallel async race.
6. **Out of scope for this slice:** FA-002 (custom auto durations/thresholds), FA-003 (temperature UI stream/initial read), FA-005 (sensor-noise restart guard), FA-006 (background lifecycle), FA-009 (settings layout).

---

## Files

**Modify:**
- `lib/src/models/preset.dart` — add `heatMode` and `heatLevel` fields with JSON migration defaults.
- `lib/src/models/preset.g.dart` — regenerate via build_runner.
- `lib/src/services/preset_service.dart` — accept/save runtime mode+level when creating presets.
- `lib/src/cubit/preset_cubit.dart` — require runtime mode+level in `savePreset`.
- `lib/src/cubit/mode_cubit.dart` — add `applyPreset`, sequential manual transitions, async `toggleHeatLevel`.
- `lib/src/presentation/app_content.dart` — delegate preset runtime apply to `ModeCubit` and await cubit calls.
- `lib/src/presentation/screens/settings/components/presets_settings.dart` — save current `ModeCubit` mode/level into presets.
- `docs/development-plan.xml` — add Phase-4 Slice-1 status/notes or update `DF-PRESET-APPLY` if wording changes.
- `docs/verification-plan.xml` — add/activate checks for preset apply and mode transition regressions.
- `docs/functional-audit-findings.md` — mark FA-001/FA-011/FA-004/FA-010 as addressed or partially addressed.

**Create:**
- `test/unit/preset_model_test.dart` — JSON defaults + round-trip for new preset fields.
- `test/unit/preset_service_test.dart` — service creates/persists runtime fields.

**Modify tests:**
- `test/unit/mode_cubit_test.dart` — add `applyPreset`, manual reset, sequential toggle assertions.

---

## Task 1: Extend `Preset` model with runtime mode/level

**Files:**
- Modify: `lib/src/models/preset.dart`
- Generate: `lib/src/models/preset.g.dart`
- Create: `test/unit/preset_model_test.dart`

- [ ] **Step 1: Write failing model tests**

Create `test/unit/preset_model_test.dart`:

```dart
// FILE: test/unit/preset_model_test.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Unit-тесты Preset JSON-контракта, включая Phase-4 runtime fields.
//   SCOPE: round-trip heatMode/heatLevel и legacy JSON defaults.
//   DEPENDS: M-PRESET, M-MANUAL-SETTINGS, M-ENUMS
//   LINKS: V-M-PRESET, FA-001, FA-011
//   ROLE: TEST
//   MAP_MODE: LOCALS
// END_MODULE_CONTRACT

import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/models/manual_settings.dart';
import 'package:autoheat/src/models/preset.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  ManualHeatSettings settings() => ManualHeatSettings.defaultFor(UserType.driver);

  // START_BLOCK_PRESET_RUNTIME_FIELDS
  test('scenario-runtime-fields: Preset JSON сохраняет heatMode и heatLevel', () {
    final preset = Preset(
      id: 'p1',
      name: 'Зима',
      userType: UserType.driver,
      settings: settings(),
      heatMode: HeatMode.presets,
      heatLevel: 2,
      createdAt: DateTime.parse('2026-01-02T03:04:05.000'),
    );

    final json = preset.toJson();
    expect(json['heatMode'], 'presets');
    expect(json['heatLevel'], 2);

    final restored = Preset.fromJson(json);
    expect(restored.heatMode, HeatMode.presets);
    expect(restored.heatLevel, 2);
  });

  test('scenario-legacy-defaults: старый JSON без heatMode/heatLevel грузится безопасно', () {
    final legacyJson = <String, dynamic>{
      'id': 'legacy',
      'name': 'Старый пресет',
      'userType': 'passenger',
      'settings': ManualHeatSettings.defaultFor(UserType.passenger).toJson(),
      'createdAt': '2026-01-02T03:04:05.000',
      'lastUsed': null,
    };

    final restored = Preset.fromJson(legacyJson);
    expect(restored.userType, UserType.passenger);
    expect(restored.heatMode, HeatMode.presets);
    expect(restored.heatLevel, 0);
  });
  // END_BLOCK_PRESET_RUNTIME_FIELDS
}
```

- [ ] **Step 2: Run test to verify RED**

Run:

```bash
flutter test test/unit/preset_model_test.dart
```

Expected: FAIL because `Preset` has no `heatMode`/`heatLevel` constructor parameters/getters.

- [ ] **Step 3: Update `Preset` contract and implementation**

In `lib/src/models/preset.dart`, replace the class with this structure while preserving existing helpers:

```dart
import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/models/manual_settings.dart';
import 'package:equatable/equatable.dart';
import 'package:json_annotation/json_annotation.dart';

part 'preset.g.dart';

@JsonSerializable()
class Preset extends Equatable {
  final String id;
  final String name;
  @JsonKey(fromJson: _userTypeFromJson, toJson: _userTypeToJson)
  final UserType userType;
  final ManualHeatSettings settings;
  @JsonKey(fromJson: _heatModeFromJson, toJson: _heatModeToJson)
  final HeatMode heatMode;
  @JsonKey(fromJson: _heatLevelFromJson)
  final int heatLevel;
  @JsonKey(fromJson: _dateTimeFromJson, toJson: _dateTimeToJson)
  final DateTime createdAt;
  @JsonKey(fromJson: _dateTimeNullableFromJson, toJson: _dateTimeNullableToJson)
  final DateTime? lastUsed;

  const Preset({
    required this.id,
    required this.name,
    required this.userType,
    required this.settings,
    this.heatMode = HeatMode.presets,
    this.heatLevel = 0,
    required this.createdAt,
    this.lastUsed,
  });

  Preset copyWith({
    String? id,
    String? name,
    UserType? userType,
    ManualHeatSettings? settings,
    HeatMode? heatMode,
    int? heatLevel,
    DateTime? createdAt,
    DateTime? lastUsed,
  }) {
    return Preset(
      id: id ?? this.id,
      name: name ?? this.name,
      userType: userType ?? this.userType,
      settings: settings ?? this.settings,
      heatMode: heatMode ?? this.heatMode,
      heatLevel: heatLevel ?? this.heatLevel,
      createdAt: createdAt ?? this.createdAt,
      lastUsed: lastUsed ?? this.lastUsed,
    );
  }

  factory Preset.fromJson(Map<String, dynamic> json) => _$PresetFromJson(json);
  Map<String, dynamic> toJson() => _$PresetToJson(this);

  @override
  List<Object?> get props => [
        id,
        name,
        userType,
        settings,
        heatMode,
        heatLevel,
        createdAt,
        lastUsed,
      ];
}

UserType _userTypeFromJson(String json) {
  return UserType.values.firstWhere(
    (type) => type.name == json,
    orElse: () => UserType.driver,
  );
}

String _userTypeToJson(UserType userType) {
  return userType.name;
}

HeatMode _heatModeFromJson(String? json) {
  if (json == null) return HeatMode.presets;
  return HeatModeExtension.fromString(json);
}

String _heatModeToJson(HeatMode heatMode) {
  return heatMode.name;
}

int _heatLevelFromJson(Object? json) {
  if (json is num) return json.toInt().clamp(0, 3);
  return 0;
}

DateTime _dateTimeFromJson(String json) {
  return DateTime.parse(json);
}

String _dateTimeToJson(DateTime dateTime) {
  return dateTime.toIso8601String();
}

DateTime? _dateTimeNullableFromJson(String? json) {
  return json != null ? DateTime.parse(json) : null;
}

String? _dateTimeNullableToJson(DateTime? dateTime) {
  return dateTime?.toIso8601String();
}
```

Also add a MODULE_CONTRACT/MODULE_MAP if absent is not required for this task unless the project decides models are substantial; keep existing codegen pattern.

- [ ] **Step 4: Regenerate JSON code**

Run:

```bash
dart run build_runner build --delete-conflicting-outputs
```

Expected: `lib/src/models/preset.g.dart` includes `heatMode` and `heatLevel` serialization.

- [ ] **Step 5: Verify GREEN**

Run:

```bash
flutter test test/unit/preset_model_test.dart
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add lib/src/models/preset.dart lib/src/models/preset.g.dart test/unit/preset_model_test.dart
git commit -m "Add preset runtime mode fields"
```

---

## Task 2: Persist runtime mode/level when saving presets

**Files:**
- Modify: `lib/src/services/preset_service.dart`
- Modify: `lib/src/cubit/preset_cubit.dart`
- Modify: `lib/src/presentation/screens/settings/components/presets_settings.dart`
- Create: `test/unit/preset_service_test.dart`

- [ ] **Step 1: Write failing service test**

Create `test/unit/preset_service_test.dart`:

```dart
// FILE: test/unit/preset_service_test.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Unit-тесты PresetService persistence для runtime mode/level fields.
//   SCOPE: createPresetFromCurrentSettings сохраняет heatMode/heatLevel.
//   DEPENDS: M-PRESET, M-ENUMS, M-MANUAL-SETTINGS
//   LINKS: V-M-PRESET, FA-001, FA-011
//   ROLE: TEST
//   MAP_MODE: LOCALS
// END_MODULE_CONTRACT

import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/models/manual_settings.dart';
import 'package:autoheat/src/services/preset_service.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  Future<PresetService> buildService() async {
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    return PresetService(prefs);
  }

  // START_BLOCK_CREATE_PRESET_RUNTIME_FIELDS
  test('createPresetFromCurrentSettings сохраняет heatMode и heatLevel', () async {
    final service = await buildService();

    final preset = await service.createPresetFromCurrentSettings(
      name: 'Трасса',
      userType: UserType.driver,
      settings: ManualHeatSettings.defaultFor(UserType.driver),
      heatMode: HeatMode.presets,
      heatLevel: 2,
    );

    expect(preset.heatMode, HeatMode.presets);
    expect(preset.heatLevel, 2);

    final loaded = await service.getPresets(UserType.driver);
    expect(loaded.single.heatMode, HeatMode.presets);
    expect(loaded.single.heatLevel, 2);
  });
  // END_BLOCK_CREATE_PRESET_RUNTIME_FIELDS
}
```

- [ ] **Step 2: Run RED**

```bash
flutter test test/unit/preset_service_test.dart
```

Expected: FAIL because `createPresetFromCurrentSettings` has no `heatMode`/`heatLevel` parameters.

- [ ] **Step 3: Update `PresetService.createPresetFromCurrentSettings`**

In `lib/src/services/preset_service.dart`, change the method signature/body:

```dart
  Future<Preset> createPresetFromCurrentSettings({
    required String name,
    required UserType userType,
    required ManualHeatSettings settings,
    required HeatMode heatMode,
    required int heatLevel,
  }) async {
    final preset = Preset(
      id: DateTime.now().millisecondsSinceEpoch.toString(),
      name: name,
      userType: userType,
      settings: settings,
      heatMode: heatMode,
      heatLevel: heatLevel.clamp(0, 3),
      createdAt: DateTime.now(),
    );

    await savePreset(preset);
    return preset;
  }
```

- [ ] **Step 4: Update `PresetCubit.savePreset`**

In `lib/src/cubit/preset_cubit.dart`, change the signature and call:

```dart
  Future<void> savePreset({
    required String name,
    required UserType userType,
    required ManualHeatSettings settings,
    required HeatMode heatMode,
    required int heatLevel,
  }) async {
    try {
      await _presetService.createPresetFromCurrentSettings(
        name: name,
        userType: userType,
        settings: settings,
        heatMode: heatMode,
        heatLevel: heatLevel,
      );

      await loadAllPresets();
    } catch (e) {
      emit(state.copyWith(error: e.toString()));
    }
  }
```

- [ ] **Step 5: Update settings UI save path**

In `lib/src/presentation/screens/settings/components/presets_settings.dart`, add `ModeCubit` import:

```dart
import 'package:autoheat/src/cubit/mode_cubit.dart';
```

Then update `_savePresetForUser` after selecting `settings`:

```dart
      final modeCubit = context.read<ModeCubit>();
      final heatMode = HeatModeExtension.fromString(modeCubit.getModeByUser(userType));
      final heatLevel = modeCubit.getHeatLevelByUser(userType);

      context.read<PresetCubit>().savePreset(
            name: result,
            userType: userType,
            settings: settings,
            heatMode: heatMode,
            heatLevel: heatLevel,
          );
```

- [ ] **Step 6: Verify GREEN**

```bash
flutter test test/unit/preset_service_test.dart
flutter analyze lib/src/services/preset_service.dart lib/src/cubit/preset_cubit.dart lib/src/presentation/screens/settings/components/presets_settings.dart
```

Expected: PASS / no issues.

- [ ] **Step 7: Commit**

```bash
git add lib/src/services/preset_service.dart lib/src/cubit/preset_cubit.dart lib/src/presentation/screens/settings/components/presets_settings.dart test/unit/preset_service_test.dart
git commit -m "Persist preset runtime state"
```

---

## Task 3: Add `ModeCubit.applyPreset` and fix manual/toggle state transitions

**Files:**
- Modify: `lib/src/cubit/mode_cubit.dart`
- Modify: `test/unit/mode_cubit_test.dart`

- [ ] **Step 1: Write failing ModeCubit tests**

In `test/unit/mode_cubit_test.dart`, add import:

```dart
import 'package:autoheat/src/models/manual_settings.dart';
import 'package:autoheat/src/models/preset.dart';
```

Add helper below `buildCubit`:

```dart
  Preset preset({
    UserType userType = UserType.driver,
    HeatMode heatMode = HeatMode.presets,
    int heatLevel = 2,
  }) {
    return Preset(
      id: 'preset-${userType.name}',
      name: 'Зима ${userType.name}',
      userType: userType,
      settings: ManualHeatSettings.defaultFor(userType),
      heatMode: heatMode,
      heatLevel: heatLevel,
      createdAt: DateTime.parse('2026-01-02T03:04:05.000'),
    );
  }
```

Add tests before `START_BLOCK_AUTO_STOP`:

```dart
  // START_BLOCK_APPLY_PRESET
  test('scenario-9: applyPreset выставляет mode/level, prefs и HVAC', () async {
    final (cubit, fakeHvac, prefs) = await buildCubit({});

    await cubit.applyPreset(preset(heatMode: HeatMode.presets, heatLevel: 2));

    expect(stateOf(cubit, UserType.driver).heatMode, HeatMode.presets);
    expect(stateOf(cubit, UserType.driver).heatLevel, 2);
    expect(prefs.getString('driver_mode'), 'presets');
    expect(prefs.getInt('driver_heat_level'), 2);
    expect(fakeHvac.recordedSetSeatHeatCalls, [
      (userType: UserType.driver, level: 2),
    ]);
    expect(
      logs.lines,
      contains('[ModeCubit][applyPreset][BLOCK_APPLY_PRESET] applied | userType=driver, mode=presets, level=2, presetId=preset-driver'),
    );
  });

  test('scenario-10: setMode manual с активным уровнем отправляет HVAC 0', () async {
    final (cubit, fakeHvac, prefs) = await buildCubit({});
    await cubit.setHeatLevel(UserType.driver, 3);
    fakeHvac.recordedSetSeatHeatCalls.clear();

    await cubit.setMode(UserType.driver, HeatMode.manual.name);

    expect(stateOf(cubit, UserType.driver).heatMode, HeatMode.manual);
    expect(stateOf(cubit, UserType.driver).heatLevel, 0);
    expect(prefs.getString('driver_mode'), 'manual');
    expect(prefs.getInt('driver_heat_level'), 0);
    expect(fakeHvac.recordedSetSeatHeatCalls, [
      (userType: UserType.driver, level: 0),
    ]);
  });

  test('scenario-11: toggleHeatLevel из presets последовательно включает manual level 1', () async {
    final (cubit, fakeHvac, prefs) = await buildCubit({});
    await cubit.applyPreset(preset(heatMode: HeatMode.presets, heatLevel: 2));
    fakeHvac.recordedSetSeatHeatCalls.clear();

    await cubit.toggleHeatLevel(UserType.driver);

    expect(stateOf(cubit, UserType.driver).heatMode, HeatMode.manual);
    expect(stateOf(cubit, UserType.driver).heatLevel, 1);
    expect(prefs.getString('driver_mode'), 'manual');
    expect(prefs.getInt('driver_heat_level'), 1);
    expect(fakeHvac.recordedSetSeatHeatCalls, [
      (userType: UserType.driver, level: 1),
    ]);
  });
  // END_BLOCK_APPLY_PRESET
```

- [ ] **Step 2: Run RED**

```bash
flutter test test/unit/mode_cubit_test.dart
```

Expected: FAIL because `ModeCubit.applyPreset` does not exist and `toggleHeatLevel` returns `void`.

- [ ] **Step 3: Update ModeCubit imports and module map**

In `lib/src/cubit/mode_cubit.dart`, add:

```dart
import 'package:autoheat/src/models/preset.dart';
```

Update MODULE_MAP lines to include:

```dart
//   applyPreset(Preset) - применить сохранённый mode/level пресета к persistence + HVAC
```

Update CHANGE_SUMMARY with Phase-4 entry.

- [ ] **Step 4: Implement sequential helpers and `applyPreset`**

In `ModeCubit`, add this private helper near `_updateUserState`:

```dart
  Future<void> _persistAndApplyHeatLevel(UserType userType, int level) async {
    await _modeService.setHeatLevel(userType, level);
    await _hvacService.setSeatHeatLevel(userType, level);
    _updateUserState(userType, heatLevel: level);
  }
```

Replace `setMode` with:

```dart
  Future<void> setMode(UserType userType, String newMode) async {
    // START_BLOCK_SET_MODE
    final heatMode = HeatModeExtension.fromString(newMode);
    final currentState = _getStateByUser(userType);

    await _modeService.setMode(userType, heatMode);
    _updateUserState(userType, mode: heatMode);

    if (heatMode == HeatMode.auto) {
      _autoHeatService.startAutoHeat(userType, (newLevel) {
        setHeatLevel(userType, newLevel);
      });
    } else {
      _autoHeatService.stopAutoHeat(userType);
    }

    if (heatMode == HeatMode.manual && currentState.heatLevel != 0) {
      await _persistAndApplyHeatLevel(userType, 0);
    } else if (heatMode == HeatMode.manual) {
      _updateUserState(userType, heatLevel: 0);
    }

    Logger.info(
      'ModeCubit',
      'setMode',
      'BLOCK_SET_MODE',
      'applied',
      {'userType': userType.name, 'mode': heatMode.name},
    );
    // END_BLOCK_SET_MODE
  }
```

Replace `setHeatLevel` with:

```dart
  Future<void> setHeatLevel(UserType userType, int level) async {
    // START_BLOCK_SET_HEAT_LEVEL
    await _persistAndApplyHeatLevel(userType, level);
    Logger.info(
      'ModeCubit',
      'setHeatLevel',
      'BLOCK_SET_HEAT_LEVEL',
      'applied',
      {'userType': userType.name, 'level': level},
    );
    // END_BLOCK_SET_HEAT_LEVEL
  }
```

Add public `applyPreset` after `setHeatLevel`:

```dart
  // START_CONTRACT: applyPreset
  //   PURPOSE: Применить сохранённый runtime mode/level пресета к конкретному сиденью.
  //   INPUTS: { preset: Preset }
  //   OUTPUTS: { Future<void> }
  //   SIDE_EFFECTS: SharedPreferences, AutoHeatService, HvacService, emit, Logger marker BLOCK_APPLY_PRESET.
  //   LINKS: M-MODE, M-PRESET, M-HVAC, M-LOGGER, V-M-MODE, V-M-PRESET, DF-PRESET-APPLY
  // END_CONTRACT: applyPreset
  Future<void> applyPreset(Preset preset) async {
    // START_BLOCK_APPLY_PRESET
    final userType = preset.userType;
    final heatMode = preset.heatMode;
    final heatLevel = preset.heatLevel.clamp(0, 3);

    if (heatMode == HeatMode.auto) {
      _autoHeatService.startAutoHeat(userType, (newLevel) {
        setHeatLevel(userType, newLevel);
      });
    } else {
      _autoHeatService.stopAutoHeat(userType);
    }

    await _modeService.setMode(userType, heatMode);
    _updateUserState(userType, mode: heatMode);
    await _persistAndApplyHeatLevel(userType, heatLevel);

    Logger.info(
      'ModeCubit',
      'applyPreset',
      'BLOCK_APPLY_PRESET',
      'applied',
      {
        'userType': userType.name,
        'mode': heatMode.name,
        'level': heatLevel,
        'presetId': preset.id,
      },
    );
    // END_BLOCK_APPLY_PRESET
  }
```

Replace `toggleHeatLevel` with async sequential version:

```dart
  Future<void> toggleHeatLevel(UserType userType) async {
    final currentState = _getStateByUser(userType);

    if (currentState.heatMode == HeatMode.manual) {
      final newLevel = currentState.heatLevel == 3 ? 0 : currentState.heatLevel + 1;
      await setHeatLevel(userType, newLevel);
    } else {
      await _modeService.setMode(userType, HeatMode.manual);
      _autoHeatService.stopAutoHeat(userType);
      _updateUserState(userType, mode: HeatMode.manual);
      await setHeatLevel(userType, 1);
    }
  }
```

Keep `_manageAutoHeat` for `_initializeHeatModes`, or simplify only if all tests still pass.

- [ ] **Step 5: Verify GREEN**

```bash
flutter test test/unit/mode_cubit_test.dart
flutter analyze lib/src/cubit/mode_cubit.dart
```

Expected: PASS / no issues.

- [ ] **Step 6: Commit**

```bash
git add lib/src/cubit/mode_cubit.dart test/unit/mode_cubit_test.dart
git commit -m "Apply presets through ModeCubit"
```

---

## Task 4: Wire preset apply UI to ModeCubit runtime apply

**Files:**
- Modify: `lib/src/presentation/app_content.dart`
- Optionally modify: `test/widget/presets_list_screen_test.dart` if widget harness is added in this task.

- [ ] **Step 1: Add failing widget-light test only if harness is practical**

If adding a full widget harness becomes large, skip widget test in this slice and rely on `ModeCubit.applyPreset` + service/cubit tests. Do not block the functional fix on a brittle UI mock. If implementing, create a small widget around `AppContent` only after isolating `GetIt` setup.

- [ ] **Step 2: Update `AppContent` imports**

Add:

```dart
import 'package:autoheat/src/cubit/mode_cubit.dart';
import 'package:autoheat/src/models/preset.dart';
```

- [ ] **Step 3: Make `_applyPreset` async and call ModeCubit**

Replace `_applyPreset(preset)` with:

```dart
  Future<void> _applyPreset(Preset preset) async {
    if (preset.userType == UserType.driver) {
      final currentState = context.read<ManualSettingsCubit>().state;
      await context.read<ManualSettingsCubit>().applyPresetSettings(
            preset.settings,
            currentState.passengerSettings,
          );
    } else {
      final currentState = context.read<ManualSettingsCubit>().state;
      await context.read<ManualSettingsCubit>().applyPresetSettings(
            currentState.driverSettings,
            preset.settings,
          );
    }

    await context.read<ModeCubit>().applyPreset(preset);
    await context.read<PresetCubit>().applyPreset(preset);

    if (!mounted) return;

    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          'Пресет "${preset.name}" применен для ${preset.userType == UserType.driver ? 'водителя' : 'пассажира'}',
        ),
        backgroundColor: context.themeColors.primary,
      ),
    );
  }
```

The existing callback can stay:

```dart
onPresetApplied: (preset) {
  _applyPreset(preset);
},
```

- [ ] **Step 4: Verify analyze and focused tests**

```bash
flutter analyze lib/src/presentation/app_content.dart
flutter test test/unit/mode_cubit_test.dart test/unit/preset_service_test.dart test/unit/preset_model_test.dart
```

Expected: no issues / PASS.

- [ ] **Step 5: Commit**

```bash
git add lib/src/presentation/app_content.dart
git commit -m "Wire preset UI apply to runtime mode"
```

---

## Task 5: Update GRACE docs and verification plan

**Files:**
- Modify: `docs/development-plan.xml`
- Modify: `docs/verification-plan.xml`
- Modify: `docs/functional-audit-findings.md`
- Optional Modify: `docs/knowledge-graph.xml` if new dependencies are materially represented.

- [ ] **Step 1: Update `docs/development-plan.xml`**

Add a new implementation phase after Phase-3 and before Phase-Deferred:

```xml
    <Phase-4 name="Functional hardening" status="in_progress">
      <goal>Исправить functional audit findings, где UI/state визуально отрабатывали без runtime HVAC effect.</goal>
      <step-1 module="M-PRESET,M-MODE,M-UI-PRESETS" status="done" verification="V-M-PRESET,V-M-MODE">FA-001/FA-011: Preset хранит heatMode/heatLevel; applyPreset идёт через ModeCubit до HvacService.</step-1>
      <step-2 module="M-MODE" status="done" verification="V-M-MODE">FA-004/FA-010: manual transition и tap из non-manual выполняются последовательно и применяют HVAC level.</step-2>
      <step-3 module="M-AUTO-HEAT,M-MANUAL-SETTINGS" status="pending" verification="V-M-AUTO-HEAT,V-M-MANUAL-SETTINGS">FA-002: пользовательские duration/threshold подключить к runtime auto algorithm.</step-3>
    </Phase-4>
```

Also update `DF-PRESET-APPLY` steps to mention `Preset.heatMode` and `Preset.heatLevel` if necessary.

- [ ] **Step 2: Update `docs/verification-plan.xml`**

Under `V-M-PRESET`, replace planned-only tests with actual files/checks:

```xml
        <file>test/unit/preset_model_test.dart</file>
        <file>test/unit/preset_service_test.dart</file>
```

Add checks:

```xml
        <check-1>flutter test test/unit/preset_model_test.dart test/unit/preset_service_test.dart</check-1>
        <check-2>flutter test test/unit/mode_cubit_test.dart</check-2>
```

Add scenario:

```xml
        <scenario-3 kind="success">Preset runtime fields: JSON round-trip сохраняет heatMode/heatLevel; legacy JSON без этих полей грузится как heatMode=presets, heatLevel=0.</scenario-3>
        <scenario-4 kind="success">ModeCubit.applyPreset(preset) сохраняет mode/level, вызывает HvacService.setSeatHeatLevel и эмитит state.</scenario-4>
```

Under `V-M-MODE`, add scenario for manual reset/toggle:

```xml
        <scenario-12 kind="success">setMode(manual) при активном heatLevel&gt;0 отправляет HvacService.setSeatHeatLevel(user,0), сохраняет prefs и state.</scenario-12>
        <scenario-13 kind="success">toggleHeatLevel из presets/auto выполняет последовательный переход в manual level=1 без параллельной гонки.</scenario-13>
```

- [ ] **Step 3: Update `docs/functional-audit-findings.md`**

For FA-001/FA-011/FA-004/FA-010, add a short status line:

```markdown
Status: addressed in Phase-4 Slice-1 by the commits produced while executing this plan.
```

For FA-004/FA-010, if only partial, use:

```markdown
Status: partially addressed in Phase-4 Slice-1; remaining behavior to verify on head unit.
```

- [ ] **Step 4: Run GRACE lint**

```bash
grace lint --profile standard
```

Expected: no GRACE issues.

- [ ] **Step 5: Commit**

```bash
git add docs/development-plan.xml docs/verification-plan.xml docs/functional-audit-findings.md docs/knowledge-graph.xml
git commit -m "Document Phase-4 preset hardening"
```

---

## Task 6: Full verification and push decision

**Files:**
- No source changes unless verification reveals a defect.

- [ ] **Step 1: Run focused checks**

```bash
flutter test test/unit/preset_model_test.dart test/unit/preset_service_test.dart test/unit/mode_cubit_test.dart
```

Expected: all tests pass.

- [ ] **Step 2: Run full automated gates**

```bash
flutter test
flutter analyze
grace lint --profile standard
git diff --check
```

Expected:
- `flutter test`: all tests pass.
- `flutter analyze`: no issues.
- `grace lint`: 0 issues.
- `git diff --check`: no whitespace errors.

- [ ] **Step 3: Review remaining audit findings**

Run:

```bash
rg -n "Status: addressed|Status: partially addressed|FA-00" docs/functional-audit-findings.md
```

Expected: FA-001 and FA-011 addressed; FA-004/FA-010 addressed or explicitly partial; other FA-* unchanged.

- [ ] **Step 4: Commit any verification-only doc fix**

If no files changed, skip. If docs needed small correction:

```bash
git add docs/functional-audit-findings.md docs/verification-plan.xml docs/development-plan.xml
git commit -m "Clarify Phase-4 verification status"
```

- [ ] **Step 5: Push after user confirmation**

```bash
git status --short
git push
```

Expected: clean working tree before push, push succeeds.

---

## Self-review

- Spec coverage:
  - FA-001 covered by Tasks 1–4.
  - FA-011 covered by Tasks 1, 3, 4.
  - FA-004 covered by Task 3.
  - FA-010 covered by Task 3.
  - Remaining FA-002/FA-003/FA-005/FA-006/FA-009/FA-012 intentionally out of scope and remain documented.
- Placeholder scan: no implementation step contains banned placeholder language. Task 4 explicitly limits widget-harness work to avoid brittle mock scope; core behavior remains covered by unit tests.
- Type consistency:
  - New fields: `Preset.heatMode: HeatMode`, `Preset.heatLevel: int`.
  - New method: `ModeCubit.applyPreset(Preset preset)`.
  - Updated methods: `PresetCubit.savePreset(... heatMode, heatLevel)`, `PresetService.createPresetFromCurrentSettings(... heatMode, heatLevel)`.
