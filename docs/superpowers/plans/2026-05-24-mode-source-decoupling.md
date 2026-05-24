# Mode-Source Decoupling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Разделить источники настроек для трёх HeatMode так, чтобы `auto` (системный) и `presets` (пользовательский) перестали пересекаться через `ManualSettingsCubit`.

**Architecture:**
- `HeatMode.auto` → `AutoHeatService.startAutoHeat(user, cb)` без `settings:` (fallback на `TemperatureConstants.getHeatSequence`, уже встроен в `_getSequence`).
- `HeatMode.presets` → `AutoHeatService.startAutoHeat(user, cb, settings: preset.settings)`.
- `ManualSettingsCubit`/`ManualSettingsService`/`ManualSettingsState` удаляются. `Preset.heatMode`/`Preset.heatLevel` удаляются. `ModeCubit` теряет зависимость от `ManualSettingsService`, получает `PresetService` для cold-start восстановления `presets`-режима.

**Tech Stack:** Flutter 3.6, flutter_bloc (Cubit), get_it DI, shared_preferences persistence, json_serializable models.

**Testing policy:** TDD по `M-MODE` (новое поведение `applyPreset`/`setMode`/`_initializeHeatModes`). Существующие тесты прогоняем после каждой задачи; должны оставаться зелёными.

**Spec:** `docs/superpowers/specs/2026-05-24-mode-source-decoupling-design.md`.

## Tactical decisions (locked from spec Open Q1–Q4)

1. **Settings source injection (Q1) → Variant B.** `setMode(user, mode, {ManualHeatSettings? settings})` принимает settings параметром. Caller (UI или внутренний `_initializeHeatModes`) сам резолвит preset settings.
2. **`selectedPresetId` single-writer (Q2) → `PresetCubit`.** `ModeCubit.applyPreset(preset)` перестаёт писать `selectedPresetId`. UI-flow `AppContent._applyPreset` гарантирует, что `PresetCubit.applyPreset(preset)` вызовется в той же транзакции и запишет id.
3. **Apply order (Q3) → ModeCubit берёт settings из аргумента `preset`, не из state.** Порядок вызовов `ModeCubit.applyPreset` ↔ `PresetCubit.applyPreset` в `AppContent` становится неважен. Оставляем текущий: ModeCubit сначала (поднимает алгоритм), PresetCubit вторым (обновляет lastUsed + selectedPresets).
4. **Init ordering (Q4) → `ModeCubit` читает `PresetService` напрямую при cold-start.** В `_initializeHeatModes` для каждого user'а в `HeatMode.presets`: `getSelectedPresetId(user)` + `getPresets(user)` → найти, передать `preset.settings` в `_startAutoHeat`. Если `selectedPresetId == null` или preset не найден → `setMode(user, manual)` + `setHeatLevel(user, 0)`.

---

## File map

**Create:**
- (none — refactor + delete only)

**Modify:**
- `lib/src/models/preset.dart` — удалить поля `heatMode`, `heatLevel`, их JSON-адаптеры; обновить `copyWith`, `props`, constructor.
- `lib/src/models/preset.g.dart` — регенерируется (`dart run build_runner build --delete-conflicting-outputs`).
- `lib/src/models/manual_settings.dart` — удалить `ManualSettingsState` + его helpers; оставить `ManualHeatSettings` и `AutoHeatLevel`.
- `lib/src/services/preset_service.dart` — `createPresetFromCurrentSettings` теряет `heatMode`/`heatLevel`.
- `lib/src/cubit/preset_cubit.dart` — `savePreset` теряет `heatMode`/`heatLevel`.
- `lib/src/cubit/mode_cubit.dart` — большой рефакторинг: убрать `ManualSettingsService`-зависимость, переписать `applyPreset`, `setMode`, `_startAutoHeat`, `_manageAutoHeat`, `_initializeHeatModes`, `toggleHeatLevel`.
- `lib/src/presentation/app_content.dart` — `_applyPreset` сжимается; новый handler `_onPresetsSegmentTapped`.
- `lib/src/presentation/screens/heat/heat_screen.dart` — пробрасывает callback'и в `ModeToggler`.
- `lib/src/presentation/screens/heat/components/mode_toggler.dart` — принимает `onPresetsSegmentTapped` callback и перехватывает тап сегмента `presets`.
- `lib/src/presentation/screens/presets/presets_tab.dart` — убрать `ManualSettingsCubit.initialize()` из `initState`; в `_onSave` убрать чтение `ModeCubit`-snapshot для `heatMode`/`heatLevel`; в `_onDelete` добавить fallback на manual+0 если удалённый пресет был активен и user в presets.
- `lib/src/di/service_locator.dart` — убрать регистрации `ManualSettingsService` и `ManualSettingsCubit`; `ModeCubit` получает `PresetService` вместо `ManualSettingsService`.
- `lib/src/di/app_bloc_providers.dart` — убрать `BlocProvider<ManualSettingsCubit>`.

**Tests (modify):**
- `test/unit/mode_cubit_test.dart` — переписать сценарии apply/setMode/init под новый source-of-settings.
- `test/unit/preset_service_test.dart` — убрать `heatMode`/`heatLevel` из фикстур и параметров.
- `test/unit/preset_cubit_test.dart` — то же.
- `test/unit/preset_model_test.dart` — то же.
- `test/unit/service_locator_test.dart` — убрать ожидания регистрации `ManualSettings*`; обновить `ModeCubit` ctor expectations.
- `test/widget/settings_screen_test.dart` — убрать `BlocProvider<ManualSettingsCubit>` + `ManualSettingsCubit` + `ManualSettingsService` из harness.

**Tests (delete):**
- `test/unit/manual_settings_cubit_test.dart`.

**Delete (sources):**
- `lib/src/cubit/manual_settings_cubit.dart`.
- `lib/src/services/manual_settings_service.dart`.

**GRACE artifacts to update:**
- `docs/knowledge-graph.xml` — M-MANUAL-SETTINGS удалить из Modules + CrossLink'ов; M-MODE depends обновить; M-UI-PRESETS / M-UI-HEAT depends обновить.
- `docs/development-plan.xml` — Phase-6 entry со ссылками на эту спеку и план; убрать M-MANUAL-SETTINGS.
- `docs/verification-plan.xml` — V-M-MANUAL-SETTINGS удалить; V-M-MODE / V-M-PRESET / V-M-UI-APP / V-M-UI-PRESETS обновить.

---

## Task 1: Подготовка фикстур пресет-тестов (без удаления полей)

**Goal:** Привести `Preset(...)`-конструкторы в тестах к виду без `heatMode:`/`heatLevel:`. Default'ы существующего конструктора (`HeatMode.presets` / `0`) обеспечат прежнее поведение. После этого реальное удаление полей не сломает компиляцию.

**Files:**
- Modify: `test/unit/preset_model_test.dart`
- Modify: `test/unit/preset_service_test.dart`
- Modify: `test/unit/preset_cubit_test.dart`

- [ ] **Step 1: Найти все вхождения `heatMode:` и `heatLevel:` в этих файлах**

```bash
grep -n "heatMode:\|heatLevel:" test/unit/preset_model_test.dart test/unit/preset_service_test.dart test/unit/preset_cubit_test.dart
```

- [ ] **Step 2: В каждом найденном `Preset(...)` или `createPresetFromCurrentSettings(...)` удалить аргументы `heatMode:` и `heatLevel:`**

Для каждой строки вида:

```dart
Preset(
  id: '1',
  name: 'x',
  userType: UserType.driver,
  settings: someSettings,
  heatMode: HeatMode.auto,        // ← удалить
  heatLevel: 2,                    // ← удалить
  createdAt: someDate,
),
```

убрать ровно две выделенные строки. Аналогично для `createPresetFromCurrentSettings(...)` (named-args).

- [ ] **Step 3: Найти ассерты, которые читают `preset.heatMode` или `preset.heatLevel`**

```bash
grep -n "\.heatMode\|\.heatLevel" test/unit/preset_model_test.dart test/unit/preset_service_test.dart test/unit/preset_cubit_test.dart
```

- [ ] **Step 4: Удалить такие ассерты целиком**

Если ассерт читает `.heatMode` / `.heatLevel` — удалить строку. Если он часть сравнения целого `Preset` — переключить на сравнение `id`/`name`/`userType`/`settings` (use `expect(preset.id, ...)` + `expect(preset.settings, ...)`).

- [ ] **Step 5: Прогнать тесты**

```bash
flutter test test/unit/preset_model_test.dart test/unit/preset_service_test.dart test/unit/preset_cubit_test.dart
```

Expected: все зелёные (defaults компенсируют).

- [ ] **Step 6: Прогнать весь набор**

```bash
flutter test
```

Expected: 130/130 зелёные.

- [ ] **Step 7: Commit**

```bash
git add test/unit/preset_model_test.dart test/unit/preset_service_test.dart test/unit/preset_cubit_test.dart
git commit -m "Drop heatMode/heatLevel from Preset test fixtures"
```

---

## Task 2: Удаление полей `heatMode`/`heatLevel` из модели `Preset`

**Goal:** Убрать поля и адаптеры из `preset.dart`, регенерировать `.g.dart`, обновить вызывающую сторону (`PresetService.createPresetFromCurrentSettings`, `PresetCubit.savePreset`).

**Files:**
- Modify: `lib/src/models/preset.dart`
- Modify (regenerate): `lib/src/models/preset.g.dart`
- Modify: `lib/src/services/preset_service.dart`
- Modify: `lib/src/cubit/preset_cubit.dart`
- Modify: `lib/src/presentation/screens/presets/presets_tab.dart`

- [ ] **Step 1: Обновить `lib/src/models/preset.dart`**

Заменить содержимое класса `Preset` и связанных helper'ов:

```dart
// FILE: lib/src/models/preset.dart
// VERSION: 1.2.0
// START_MODULE_CONTRACT
//   PURPOSE: JSON-модель пользовательского пресета: name, settings и metadata.
//   SCOPE: Preset, JSON adapters для UserType/DateTime. Snapshot-поля
//          heatMode/heatLevel удалены — пресет описывает только настройки,
//          mode/level определяются runtime'ом при apply.
//   DEPENDS: M-ENUMS, M-MANUAL-SETTINGS
//   LINKS: M-PRESET, V-M-PRESET, DF-PRESET-APPLY
//   ROLE: TYPES
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   Preset - immutable value object с id/name/userType/settings/createdAt/lastUsed
//   copyWith - изменить поля без потери metadata
//   fromJson/toJson - SharedPreferences JSON contract через json_serializable
//   _userTypeFromJson/_userTypeToJson - stable enum.name adapter
//   _dateTimeFromJson/_dateTimeToJson - ISO-8601 adapter
//   _dateTimeNullableFromJson/_dateTimeNullableToJson - nullable ISO-8601 adapter
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.2.0 - Mode-source decoupling: drop heatMode/heatLevel snapshot fields]
//   PREVIOUS_CHANGE: [v1.1.0 - Phase-4 Slice-1: добавлены runtime heatMode/heatLevel]
// END_CHANGE_SUMMARY

import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/models/manual_settings.dart';
import 'package:equatable/equatable.dart';
import 'package:json_annotation/json_annotation.dart';

part 'preset.g.dart';

@JsonSerializable(explicitToJson: true)
class Preset extends Equatable {
  final String id;
  final String name;
  @JsonKey(fromJson: _userTypeFromJson, toJson: _userTypeToJson)
  final UserType userType;
  final ManualHeatSettings settings;
  @JsonKey(fromJson: _dateTimeFromJson, toJson: _dateTimeToJson)
  final DateTime createdAt;
  @JsonKey(fromJson: _dateTimeNullableFromJson, toJson: _dateTimeNullableToJson)
  final DateTime? lastUsed;

  const Preset({
    required this.id,
    required this.name,
    required this.userType,
    required this.settings,
    required this.createdAt,
    this.lastUsed,
  });

  Preset copyWith({
    String? id,
    String? name,
    UserType? userType,
    ManualHeatSettings? settings,
    DateTime? createdAt,
    DateTime? lastUsed,
  }) {
    return Preset(
      id: id ?? this.id,
      name: name ?? this.name,
      userType: userType ?? this.userType,
      settings: settings ?? this.settings,
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

String _userTypeToJson(UserType userType) => userType.name;

DateTime _dateTimeFromJson(String json) => DateTime.parse(json);
String _dateTimeToJson(DateTime dateTime) => dateTime.toIso8601String();

DateTime? _dateTimeNullableFromJson(String? json) {
  return json != null ? DateTime.parse(json) : null;
}

String? _dateTimeNullableToJson(DateTime? dateTime) {
  return dateTime?.toIso8601String();
}
```

- [ ] **Step 2: Регенерация `.g.dart`**

```bash
dart run build_runner build --delete-conflicting-outputs
```

Expected: `Succeeded after ...`. Файл `lib/src/models/preset.g.dart` обновится — больше не упоминает `heatMode`/`heatLevel`.

- [ ] **Step 3: Обновить `PresetService.createPresetFromCurrentSettings`**

В `lib/src/services/preset_service.dart` заменить контракт и реализацию метода:

```dart
  // START_CONTRACT: createPresetFromCurrentSettings
  //   PURPOSE: Создать snapshot пресета из настроек user'а.
  //   INPUTS: { name, userType, settings }
  //   OUTPUTS: { Future<Preset> }
  //   SIDE_EFFECTS: SharedPreferences write через savePreset.
  //   LINKS: M-PRESET, V-M-PRESET
  // END_CONTRACT: createPresetFromCurrentSettings
  Future<Preset> createPresetFromCurrentSettings({
    required String name,
    required UserType userType,
    required ManualHeatSettings settings,
  }) async {
    // START_BLOCK_CREATE_PRESET_FROM_CURRENT_SETTINGS
    final preset = Preset(
      id: DateTime.now().millisecondsSinceEpoch.toString(),
      name: name,
      userType: userType,
      settings: settings,
      createdAt: DateTime.now(),
    );

    await savePreset(preset);
    return preset;
    // END_BLOCK_CREATE_PRESET_FROM_CURRENT_SETTINGS
  }
```

Также удалить упоминания `heatMode`/`heatLevel` из `MODULE_MAP` и `CHANGE_SUMMARY` шапки файла; bump `LAST_CHANGE`:

```dart
//   LAST_CHANGE: [v1.3.0 - Mode-source decoupling: drop heatMode/heatLevel from createPresetFromCurrentSettings]
//   PREVIOUS_CHANGE: [v1.2.0 - Phase-4 Slice-1: createPresetFromCurrentSettings принимает heatMode/heatLevel]
```

- [ ] **Step 4: Обновить `PresetCubit.savePreset`**

В `lib/src/cubit/preset_cubit.dart` заменить сигнатуру метода и тело:

```dart
  // START_CONTRACT: savePreset
  //   PURPOSE: Сохранить пресет как snapshot settings.
  //   INPUTS: { name, userType, settings }
  //   OUTPUTS: { Future<void> }
  //   SIDE_EFFECTS: SharedPreferences write через PresetService, reload list.
  //   LINKS: M-PRESET, V-M-PRESET
  // END_CONTRACT: savePreset
  Future<void> savePreset({
    required String name,
    required UserType userType,
    required ManualHeatSettings settings,
  }) async {
    // START_BLOCK_SAVE_PRESET
    try {
      await _presetService.createPresetFromCurrentSettings(
        name: name,
        userType: userType,
        settings: settings,
      );

      await loadAllPresets();
    } catch (e) {
      emit(state.copyWith(error: e.toString()));
    }
    // END_BLOCK_SAVE_PRESET
  }
```

Bump CHANGE_SUMMARY:

```dart
//   LAST_CHANGE: [v1.4.0 - Mode-source decoupling: savePreset больше не принимает heatMode/heatLevel snapshot]
//   PREVIOUS_CHANGE: [v1.3.0 - Add updatePresetSettings for in-place editing of saved presets]
```

- [ ] **Step 5: Обновить `PresetsTab._onSave`**

В `lib/src/presentation/screens/presets/presets_tab.dart` найти ветку `_isNewPresetDraft` внутри `_onSave` и заменить её на:

```dart
    if (_isNewPresetDraft) {
      final name = await showDialog<String>(
        context: context,
        builder: (_) => const SavePresetDialog(),
      );
      if (name == null || name.trim().isEmpty) return;
      if (!mounted) return;

      await context.read<PresetCubit>().savePreset(
            name: name.trim(),
            userType: _selectedUser,
            settings: draft,
          );

      if (!mounted) return;
      setState(() {
        _isNewPresetDraft = false;
        _draftSettings = null;
        _editingPresetId = null;
      });
      return;
    }
```

Также удалить теперь неиспользуемые импорты в этом файле:

```dart
import 'package:autoheat/src/cubit/mode_cubit.dart';
import 'package:autoheat/src/app_enums.dart';  // если не используется чем-то ещё
```

Запустить `flutter analyze lib/src/presentation/screens/presets/presets_tab.dart` после правки и удалить именно те импорты, на которые ругнётся analyzer как «unused_import».

- [ ] **Step 6: Прогнать analyzer**

```bash
flutter analyze
```

Expected: `No issues found!`. Если есть ошибки про `heatMode`/`heatLevel` — поправить вызывающие места по тексту ошибки (это будут tests; они уже в Task 1 чистые, но проверим).

- [ ] **Step 7: Прогнать тесты**

```bash
flutter test
```

Expected: все зелёные.

- [ ] **Step 8: Commit**

```bash
git add lib/src/models/preset.dart lib/src/models/preset.g.dart \
        lib/src/services/preset_service.dart lib/src/cubit/preset_cubit.dart \
        lib/src/presentation/screens/presets/presets_tab.dart
git commit -m "Drop Preset.heatMode and Preset.heatLevel snapshot fields"
```

---

## Task 3: Failing tests для нового поведения `ModeCubit`

**Goal:** Зафиксировать ожидания нового `ModeCubit`: `applyPreset` всегда переводит в `HeatMode.presets` и стартует AutoHeatService с `preset.settings`; `setMode(auto)` стартует без settings; `setMode(presets, settings:)` стартует с settings; `_initializeHeatModes` для presets читает `PresetService`.

**Files:**
- Modify: `test/unit/mode_cubit_test.dart`
- Modify: `test/_helpers/fake_hvac_service.dart` (если потребуется — обычно нет; FakeHvacService уже подходит).

Перед началом — посмотреть, как организован файл сейчас:

```bash
grep -n "scenario-\|test(\|testWidgets\|setUp\|tearDown\|group(" test/unit/mode_cubit_test.dart | head -40
```

- [ ] **Step 1: Обновить конструктор `ModeCubit` во всех `setUp`-блоках теста**

Сейчас тест собирает `ModeCubit(modeService, hvacService, manualSettingsService, presetService)`. После рефакторинга подпись будет `ModeCubit(modeService, hvacService, presetService)`. В тесте нужно соответственно убрать аргумент `manualSettingsService` и связанные mock'и.

Замените локальные конструкторы в setUp. Пример:

```dart
// было:
modeCubit = ModeCubit(modeService, hvacService, manualSettingsService, presetService);

// станет:
modeCubit = ModeCubit(modeService, hvacService, presetService);
```

Также удалите создание и инициализацию `manualSettingsService` mock'а в setUp (`when(() => manualSettingsService.getSettings(...))` — больше не нужно).

- [ ] **Step 2: Добавить новый тест: `applyPreset` всегда переводит в `HeatMode.presets`**

```dart
test('scenario-N: applyPreset всегда выставляет HeatMode.presets, игнорируя legacy snapshot', () async {
  // arrange
  final settings = ManualHeatSettings.defaultFor(UserType.driver);
  final preset = Preset(
    id: 'p1',
    name: 'My preset',
    userType: UserType.driver,
    settings: settings,
    createdAt: DateTime.now(),
  );

  // act
  await modeCubit.applyPreset(preset);

  // assert
  expect(modeCubit.getModeByUser(UserType.driver), HeatMode.presets.name);
  verify(() => modeService.setMode(UserType.driver, HeatMode.presets)).called(1);
});
```

- [ ] **Step 3: Добавить тест: `applyPreset` стартует AutoHeatService с `preset.settings`**

```dart
test('scenario-N+1: applyPreset стартует AutoHeatService с preset.settings', () async {
  final customSettings = ManualHeatSettings(
    autoHeatLevels: [
      const AutoHeatLevel(duration: 7, level: 1),
      const AutoHeatLevel(duration: 9, level: 2),
      const AutoHeatLevel(duration: 11, level: 3),
    ],
    temperatureThreshold: 8.0,
  );
  final preset = Preset(
    id: 'p2', name: 'Custom',
    userType: UserType.driver,
    settings: customSettings,
    createdAt: DateTime.now(),
  );

  fakeHvacService.programmedTemperature = -5; // ниже threshold
  await modeCubit.applyPreset(preset);

  // FakeHvacService.recordedSetSeatHeatCalls должен зафиксировать апликейшн
  // соответствующий настройкам preset (по temperature: уровень 3 первым).
  final calls = fakeHvacService.recordedSetSeatHeatCalls;
  expect(calls.any((c) => c.$1 == UserType.driver && c.$2 == 3), isTrue);
});
```

Если FakeHvacService напрямую к ModeCubit не подключён (там используется FakeHvacService для seeding температуры через `seedCurrentTemperatureFromHvac`) — оставьте этот сценарий за `_startAutoHeat`-side: при необходимости smoke только что mock-вызов `_autoHeatService.startAutoHeat(...)` пришёл с `settings: customSettings`. Если AutoHeatService — реальный синглтон в тесте, проверяйте через FakeHvacService.recordedSetSeatHeatCalls.

- [ ] **Step 4: Добавить тест: `setMode(auto)` стартует AutoHeatService без settings (fallback на TemperatureConstants)**

```dart
test('scenario-N+2: setMode(auto) стартует AutoHeatService без settings → algorithm читает TemperatureConstants', () async {
  fakeHvacService.programmedTemperature = -3;
  await modeCubit.setMode(UserType.driver, HeatMode.auto.name);

  expect(modeCubit.getModeByUser(UserType.driver), HeatMode.auto.name);
  // С programmedTemperature = -3 и TemperatureConstants.cold → уровень 3 первым.
  final calls = fakeHvacService.recordedSetSeatHeatCalls;
  expect(calls.any((c) => c.$1 == UserType.driver && c.$2 == 3), isTrue);
});
```

- [ ] **Step 5: Добавить тест: `_initializeHeatModes` восстанавливает `presets`-режим через `PresetService`**

```dart
test('scenario-N+3: cold-start с persisted (presets, selectedPresetId) восстанавливает алгоритм с preset.settings', () async {
  // arrange: ModeService уже возвращает driver=(presets, 0); PresetService содержит preset с id=p1.
  SharedPreferences.setMockInitialValues({
    'driver_mode': HeatMode.presets.name,
    'driver_heat_level': 0,
    'selected_preset_id_driver': 'p1',
    // ... presets_driver = [{id:p1, settings:{...}}]
  });
  // Пересобрать ModeCubit с этими prefs.

  final cubit = ModeCubit(realModeService, fakeHvacService, realPresetService);
  await Future<void>.delayed(Duration.zero); // дать _initialize отработать

  expect(cubit.getModeByUser(UserType.driver), HeatMode.presets.name);
  // AutoHeat должен запуститься с p1 settings — проверяем через fakeHvacService.recordedSetSeatHeatCalls.
});
```

- [ ] **Step 6: Добавить тест: cold-start с (presets, missing selectedPresetId) → fallback на manual+0**

```dart
test('scenario-N+4: cold-start с presets но без selectedPresetId — fallback на manual heatLevel=0', () async {
  SharedPreferences.setMockInitialValues({
    'driver_mode': HeatMode.presets.name,
    'driver_heat_level': 0,
    // selected_preset_id_driver отсутствует
  });
  final cubit = ModeCubit(realModeService, fakeHvacService, realPresetService);
  await Future<void>.delayed(Duration.zero);

  expect(cubit.getModeByUser(UserType.driver), HeatMode.manual.name);
  expect(cubit.getHeatLevelByUser(UserType.driver), 0);
});
```

- [ ] **Step 7: Прогнать тесты — должны падать**

```bash
flutter test test/unit/mode_cubit_test.dart
```

Expected: новые сценарии красные (старый код не реализует эти инварианты). Старые сценарии могут падать только в `applyPreset`-сценариях, проверяющих устаревшее поведение «preset.heatMode → mode». Если такие старые сценарии стали мертвы по новому API — пометить как `// removed after mode-source decoupling` и удалить тело (или удалить целиком). Документировать причину коротко в комментарии.

- [ ] **Step 8: Commit (failing tests are intentional baseline)**

```bash
git add test/unit/mode_cubit_test.dart
git commit -m "Add failing tests for decoupled ModeCubit behavior

Tests will go green in Task 4 when ModeCubit is refactored to:
- applyPreset always emits HeatMode.presets
- setMode(auto) starts AutoHeatService without custom settings
- _initializeHeatModes restores presets-mode via PresetService"
```

---

## Task 4: Рефакторинг `ModeCubit`

**Goal:** Сделать тесты из Task 3 зелёными.

**Files:**
- Modify: `lib/src/cubit/mode_cubit.dart`
- Modify: `lib/src/di/service_locator.dart` (передать `PresetService` вместо `ManualSettingsService` в `ModeCubit` constructor).

- [ ] **Step 1: Переписать `lib/src/cubit/mode_cubit.dart`**

Заменить заголовок и тело файла на новый. Не трогаем `ModeState`/`ModesState` модели (они в отдельном файле и не меняются), только класс `ModeCubit`.

Конкретные изменения:

a) Импорт: убрать `manual_settings_service.dart`. Оставить `preset_service.dart`.

b) Поля: убрать `_manualSettingsService`; ничего нового не добавлять — `_presetService` уже есть.

c) Конструктор:

```dart
ModeCubit(
  this._modeService,
  this._hvacService,
  this._presetService,
) : super(ModesState(states: [
        ModeState(userType: UserType.driver, heatMode: HeatMode.manual, heatLevel: 0),
        ModeState(userType: UserType.passenger, heatMode: HeatMode.manual, heatLevel: 0),
      ])) {
  _initialize();
}
```

d) `setMode` теперь принимает optional `settings` параметр:

```dart
// START_CONTRACT: setMode
//   PURPOSE: Сменить режим сиденья, persist и запустить/остановить авторежим.
//   INPUTS: { userType: UserType, newMode: String HeatMode.name, settings?: ManualHeatSettings }
//   OUTPUTS: { Future<void> }
//   SIDE_EFFECTS: SharedPreferences, AutoHeatService, emit, Logger marker BLOCK_SET_MODE.
//                 НЕ пишет selectedPresetId — это owned by PresetCubit.
//   LINKS: M-MODE, M-AUTO-HEAT, M-LOGGER, V-M-MODE, DF-AUTO-HEAT
// END_CONTRACT: setMode
Future<void> setMode(
  UserType userType,
  String newMode, {
  ManualHeatSettings? settings,
}) async {
  // START_BLOCK_SET_MODE
  final heatMode = HeatModeExtension.fromString(newMode);
  final currentState = _getStateByUser(userType);

  await _modeService.setMode(userType, heatMode);
  _updateUserState(userType, mode: heatMode);

  await _manageAutoHeat(userType, heatMode, settings: settings);

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

Заметьте: `clearSelectedPresetId` отсюда **убран** — это теперь зона ответственности `PresetCubit` (см. tactical decision 2). `PresetCubit.applyPreset` и existing `PresetService.deletePreset` уже корректно работают с этим.

e) `applyPreset`:

```dart
// START_CONTRACT: applyPreset
//   PURPOSE: Применить user preset как presets-режим с его settings.
//   INPUTS: { preset: Preset }
//   OUTPUTS: { Future<void> }
//   SIDE_EFFECTS: SharedPreferences (mode), AutoHeatService start с preset.settings, emit.
//                 НЕ пишет selectedPresetId — это owned by PresetCubit (caller).
//   LINKS: M-MODE, M-PRESET, M-AUTO-HEAT, M-LOGGER, V-M-MODE, V-M-PRESET, DF-PRESET-APPLY
// END_CONTRACT: applyPreset
Future<void> applyPreset(Preset preset) async {
  // START_BLOCK_APPLY_PRESET
  await setMode(
    preset.userType,
    HeatMode.presets.name,
    settings: preset.settings,
  );

  Logger.info(
    'ModeCubit',
    'applyPreset',
    'BLOCK_APPLY_PRESET',
    'applied',
    {
      'userType': preset.userType.name,
      'presetId': preset.id,
    },
  );
  // END_BLOCK_APPLY_PRESET
}
```

f) `_manageAutoHeat`:

```dart
Future<void> _manageAutoHeat(
  UserType userType,
  HeatMode heatMode, {
  ManualHeatSettings? settings,
}) async {
  switch (heatMode) {
    case HeatMode.manual:
      _autoHeatService.stopAutoHeat(userType);
      return;
    case HeatMode.auto:
      await _startAutoHeat(userType, settings: null);
      return;
    case HeatMode.presets:
      if (settings == null) {
        // Defensive: UI не должна слать setMode(presets) без settings.
        // Логируем и не меняем алгоритм-state.
        Logger.warn(
          'ModeCubit',
          '_manageAutoHeat',
          'BLOCK_MANAGE_AUTO_HEAT',
          'presets-without-settings (defensive no-op)',
          {'userType': userType.name},
        );
        return;
      }
      await _startAutoHeat(userType, settings: settings);
      return;
  }
}
```

g) `_startAutoHeat` принимает settings:

```dart
Future<void> _startAutoHeat(
  UserType userType, {
  required ManualHeatSettings? settings,
}) async {
  Future<void>? immediateHeatLevelFuture;

  void handleAutoHeatLevel(int newLevel) {
    final future = setHeatLevel(userType, newLevel);
    immediateHeatLevelFuture ??= future;
  }

  _autoHeatService.startAutoHeat(
    userType,
    handleAutoHeatLevel,
    settings: settings, // null → AutoHeatService fallback to TemperatureConstants
  );

  await immediateHeatLevelFuture;
}
```

h) `_initializeHeatModes` обновляется чтобы поддерживать `presets`:

```dart
Future<void> _initializeHeatModes(List<ModeState> states) async {
  for (final state in states) {
    switch (state.heatMode) {
      case HeatMode.manual:
        if (state.heatLevel > 0) {
          // ignore: discarded_futures
          setHeatLevel(state.userType, state.heatLevel);
        }
        break;
      case HeatMode.auto:
        await _startAutoHeat(state.userType, settings: null);
        break;
      case HeatMode.presets:
        final presetSettings = await _resolveSelectedPresetSettings(state.userType);
        if (presetSettings == null) {
          // Нет валидного preset — откат на manual+0.
          await _modeService.setMode(state.userType, HeatMode.manual);
          _updateUserState(state.userType, mode: HeatMode.manual, heatLevel: 0);
          await _modeService.setHeatLevel(state.userType, 0);
        } else {
          await _startAutoHeat(state.userType, settings: presetSettings);
        }
        break;
    }
  }
}

Future<ManualHeatSettings?> _resolveSelectedPresetSettings(UserType userType) async {
  final selectedId = await _presetService.getSelectedPresetId(userType);
  if (selectedId == null) return null;
  final presets = await _presetService.getPresets(userType);
  for (final p in presets) {
    if (p.id == selectedId) return p.settings;
  }
  return null;
}
```

i) `toggleHeatLevel` упрощается — `clearSelectedPresetId` оттуда тоже уходит (зону ответственности перенесли в `PresetCubit`):

```dart
Future<void> toggleHeatLevel(UserType userType) async {
  final currentState = _getStateByUser(userType);

  if (currentState.heatMode == HeatMode.manual) {
    final newLevel =
        currentState.heatLevel == 3 ? 0 : currentState.heatLevel + 1;
    await setHeatLevel(userType, newLevel);
  } else {
    await _modeService.setMode(userType, HeatMode.manual);
    _autoHeatService.stopAutoHeat(userType);
    _updateUserState(userType, mode: HeatMode.manual);
    await setHeatLevel(userType, 1);
  }
}
```

j) Обновить шапку файла:

```dart
//   SCOPE: ModeCubit над ModesState; setMode/applyPreset/toggleHeatLevel/setHeatLevel,
//          source-of-settings routing для AutoHeatService: auto→TemperatureConstants,
//          presets→preset.settings, manual→stop.
//   DEPENDS: M-MODE, M-HVAC, M-AUTO-HEAT, M-PRESET, M-ENUMS, M-LOGGER
//   ...
//   LAST_CHANGE: [v1.3.0 - Mode-source decoupling: drop ManualSettingsService, источник settings приходит из caller или PresetService]
//   PREVIOUS_CHANGE: [v1.2.0 - Phase-4: applyPreset до HvacService, sequential transitions]
```

Удалить упоминания `M-MANUAL-SETTINGS` из `DEPENDS`.

- [ ] **Step 2: Обновить `service_locator.dart`**

Найти регистрацию `ModeCubit`:

```dart
() => ModeCubit(
      locator<ModeService>(),
      locator<HvacService>(),
      locator<ManualSettingsService>(),
      locator<PresetService>(),
    ),
```

Заменить на:

```dart
() => ModeCubit(
      locator<ModeService>(),
      locator<HvacService>(),
      locator<PresetService>(),
    ),
```

- [ ] **Step 3: Прогнать analyzer**

```bash
flutter analyze lib/src/cubit/mode_cubit.dart lib/src/di/service_locator.dart
```

Expected: возможно появятся warnings об unused `manual_settings_service` import / поле в service_locator — поправить (импорт `ManualSettingsService` пока оставить, удалим в Task 9). Если других ошибок нет — ок.

- [ ] **Step 4: Прогнать ModeCubit тесты**

```bash
flutter test test/unit/mode_cubit_test.dart
```

Expected: новые сценарии зелёные. Если кое-какие старые сценарии всё ещё красные (например, проверяли `preset.heatMode` snapshot или `selectedPresetId` запись через ModeCubit) — переписать или удалить как часть этого же шага.

- [ ] **Step 5: Прогнать весь набор**

```bash
flutter test
```

Expected: все зелёные. Если падает `service_locator_test.dart` — поправить (Task 10 предполагает обновление, но кое-что может сломаться раньше; правим прямо здесь).

- [ ] **Step 6: Commit**

```bash
git add lib/src/cubit/mode_cubit.dart lib/src/di/service_locator.dart test/unit/mode_cubit_test.dart
git commit -m "Refactor ModeCubit: decouple auto-mode from user presets

- ModeCubit constructor drops ManualSettingsService dep
- applyPreset always emits HeatMode.presets and uses preset.settings directly
- setMode(mode, {settings}) routes settings to AutoHeatService:
  manual→stop, auto→start without settings (TemperatureConstants), presets→start with settings
- _initializeHeatModes restores presets-mode via PresetService.getSelectedPresetId/getPresets
- selectedPresetId is no longer written by ModeCubit (PresetCubit single-writer)
- toggleHeatLevel no longer clears selectedPresetId either"
```

---

## Task 5: `ModeToggler` — гард на сегмент «Пресеты»

**Goal:** Тап на сегмент «Пресеты» без активного пресета у этого user'а не должен «защёлкивать» сегмент — должен пробрасываться на `AppContent` через callback. С активным пресетом — обычное `applyPreset` (так чтобы и `PresetCubit.applyPreset`, и `ModeCubit.applyPreset` отработали).

**Files:**
- Modify: `lib/src/presentation/screens/heat/components/mode_toggler.dart`
- Modify: `lib/src/presentation/screens/heat/heat_screen.dart`
- Modify: `lib/src/presentation/app_content.dart`

- [ ] **Step 1: Обновить `ModeToggler`**

Заменить содержимое `lib/src/presentation/screens/heat/components/mode_toggler.dart`:

```dart
// FILE: lib/src/presentation/screens/heat/components/mode_toggler.dart
// VERSION: 1.1.0
// START_MODULE_CONTRACT
//   PURPOSE: SegmentedButton для выбора HeatMode (manual/presets/auto) per UserType.
//   SCOPE: тап-handler разводит presets-сегмент через onPresetsSegmentTapped,
//          чтобы AppContent мог либо открыть Presets-вкладку, либо применить активный
//          пресет.
//   DEPENDS: M-UI-HEAT, M-MODE, M-ENUMS
//   LINKS: M-UI-HEAT, V-M-UI-HEAT
//   ROLE: RUNTIME
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   ModeToggler - StatelessWidget c ValueChanged-like callback на presets-сегмент
//   _handleSelection - dispatches к ModeCubit.setMode для manual/auto;
//                       вызывает onPresetsSegmentTapped для presets
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.1.0 - Mode-source decoupling: presets-segment routes through AppContent callback]
// END_CHANGE_SUMMARY

import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/cubit/mode_cubit.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

class ModeToggler extends StatelessWidget {
  final UserType user;
  final VoidCallback onPresetsSegmentTapped;

  const ModeToggler({
    super.key,
    required this.user,
    required this.onPresetsSegmentTapped,
  });

  @override
  Widget build(BuildContext context) {
    final ModeCubit cubit = context.watch<ModeCubit>();
    final String selected = cubit.getModeByUser(user);

    void handleSelection(Set<String> newSelection) {
      final value = newSelection.first;
      if (value == HeatMode.presets.name) {
        onPresetsSegmentTapped();
        return;
      }
      cubit.setMode(user, value);
    }

    return Row(
      children: [
        SegmentedButton<String>(
          segments: const [
            ButtonSegment(
              value: 'manual',
              label: Text('Вручную'),
              icon: Icon(Icons.touch_app),
            ),
            ButtonSegment(
              value: 'presets',
              label: Text('Пресеты'),
              icon: Icon(Icons.settings),
            ),
            ButtonSegment(
              value: 'auto',
              label: Text('Авто'),
              icon: Icon(Icons.hdr_auto),
            ),
          ],
          selected: {selected},
          onSelectionChanged: handleSelection,
          showSelectedIcon: false,
        ),
      ],
    );
  }
}
```

(Значения сегментов оставлены строками, чтобы не лезть в `HeatMode.values.name`-генерацию; они стабильны.)

- [ ] **Step 2: Обновить `HeatScreen`**

В `lib/src/presentation/screens/heat/heat_screen.dart`:

a) Добавить required field `onPresetsSegmentTapped`:

```dart
class HeatScreen extends StatelessWidget {
  final void Function(UserType user) onPresetsSegmentTapped;

  const HeatScreen({super.key, required this.onPresetsSegmentTapped});
  // ...
}
```

b) Найти оба места, где создаётся `ModeToggler(user: UserType.driver)` и `ModeToggler(user: UserType.passenger)`, и заменить на:

```dart
ModeToggler(
  user: UserType.driver,
  onPresetsSegmentTapped: () => onPresetsSegmentTapped(UserType.driver),
),
// ...
ModeToggler(
  user: UserType.passenger,
  onPresetsSegmentTapped: () => onPresetsSegmentTapped(UserType.passenger),
),
```

c) Bump шапку:

```dart
//   LAST_CHANGE: [v1.4.0 - Mode-source decoupling: pass onPresetsSegmentTapped callback to ModeToggler]
//   PREVIOUS_CHANGE: [v1.3.0 - Restore plain Column; selector reserves layout space via Visibility(maintainSize)]
```

- [ ] **Step 3: Обновить `AppContent`**

В `lib/src/presentation/app_content.dart`:

a) Заменить `HeatScreen()` в `TabBarView.children` на:

```dart
HeatScreen(onPresetsSegmentTapped: _onPresetsSegmentTapped),
```

b) Добавить метод `_onPresetsSegmentTapped` в state:

```dart
void _onPresetsSegmentTapped(UserType user) {
  final presetCubit = context.read<PresetCubit>();
  final activePreset = presetCubit.state.selectedPresets[user];
  if (activePreset == null) {
    _selectTab(1); // tab «Пресеты»
    return;
  }
  // С активным пресетом — обычный apply flow (тот же, что у ▶ apply в списке).
  _applyPreset(activePreset);
}
```

c) Bump шапку с новой версией:

```dart
//   LAST_CHANGE: [v1.3.0 - Mode-source decoupling: onPresetsSegmentTapped routes to apply or tab navigation]
//   PREVIOUS_CHANGE: [v1.2.0 - Settings/Presets UX redesign: tab order Управление→Пресеты→Настройки, merged PresetsTab]
```

- [ ] **Step 4: Прогнать analyzer**

```bash
flutter analyze lib/src/presentation/screens/heat/components/mode_toggler.dart \
                lib/src/presentation/screens/heat/heat_screen.dart \
                lib/src/presentation/app_content.dart
```

Expected: `No issues found!`.

- [ ] **Step 5: Прогнать тесты**

```bash
flutter test
```

Expected: все зелёные. Если падают widget-тесты, использующие `HeatScreen` или `ModeToggler` — обновить их под новый API (передавать stub-callback).

- [ ] **Step 6: Commit**

```bash
git add lib/src/presentation/screens/heat/components/mode_toggler.dart \
        lib/src/presentation/screens/heat/heat_screen.dart \
        lib/src/presentation/app_content.dart
git commit -m "Route ModeToggler presets-segment through AppContent

Tapping 'Пресеты' segment with no active preset for this user opens the
Presets tab instead of latching the segment into a broken presets-without-
preset state. With an active preset it triggers the same apply flow as
the ▶ icon in the preset list (ModeCubit.applyPreset + PresetCubit.applyPreset)."
```

---

## Task 6: Убрать `manualSettingsCubit.applyPresetSettings` из `AppContent._applyPreset`

**Goal:** Финализировать развязку — apply больше не пишет в `ManualSettingsCubit`.

**Files:**
- Modify: `lib/src/presentation/app_content.dart`

- [ ] **Step 1: Заменить тело `_applyPreset`**

Найти текущий `_applyPreset`:

```dart
Future<void> _applyPreset(Preset preset) async {
  final manualSettingsCubit = context.read<ManualSettingsCubit>();
  final modeCubit = context.read<ModeCubit>();
  final presetCubit = context.read<PresetCubit>();

  if (preset.userType == UserType.driver) {
    final currentState = manualSettingsCubit.state;
    await manualSettingsCubit.applyPresetSettings(
      preset.settings,
      currentState.passengerSettings,
    );
  } else {
    final currentState = manualSettingsCubit.state;
    await manualSettingsCubit.applyPresetSettings(
      currentState.driverSettings,
      preset.settings,
    );
  }

  await modeCubit.applyPreset(preset);
  await presetCubit.applyPreset(preset);

  if (!mounted) return;

  ScaffoldMessenger.of(context).showSnackBar(...);
}
```

Заменить на:

```dart
// START_CONTRACT: _applyPreset
//   PURPOSE: Применить пользовательский Preset к runtime через ModeCubit и PresetCubit.
//   INPUTS: { preset: Preset }
//   OUTPUTS: { Future<void> }
//   SIDE_EFFECTS: ModeCubit.applyPreset (mode=presets + AutoHeatService restart),
//                 PresetCubit.applyPreset (selectedPresetId + lastUsed), SnackBar.
//   LINKS: M-UI-PRESETS, M-PRESET, M-MODE, M-HVAC, DF-PRESET-APPLY, FA-001, FA-011
// END_CONTRACT: _applyPreset
Future<void> _applyPreset(Preset preset) async {
  // START_BLOCK_APPLY_PRESET
  final modeCubit = context.read<ModeCubit>();
  final presetCubit = context.read<PresetCubit>();

  await modeCubit.applyPreset(preset);
  await presetCubit.applyPreset(preset);

  if (!mounted) return;

  ScaffoldMessenger.of(context).showSnackBar(
    SnackBar(
      content: Text(
        'Пресет "${preset.name}" применен для ${preset.userType == UserType.driver ? 'водителя' : 'пассажира'}',
        style: TextStyle(color: context.themeColors.textButtonSelected),
      ),
      backgroundColor: context.themeColors.primary,
    ),
  );
  // END_BLOCK_APPLY_PRESET
}
```

- [ ] **Step 2: Удалить теперь неиспользуемый импорт**

В `lib/src/presentation/app_content.dart` удалить:

```dart
import 'package:autoheat/src/cubit/manual_settings_cubit.dart';
```

- [ ] **Step 3: Прогнать analyzer**

```bash
flutter analyze lib/src/presentation/app_content.dart
```

Expected: `No issues found!`.

- [ ] **Step 4: Прогнать тесты**

```bash
flutter test
```

Expected: все зелёные.

- [ ] **Step 5: Commit**

```bash
git add lib/src/presentation/app_content.dart
git commit -m "Drop ManualSettingsCubit.applyPresetSettings from _applyPreset

Preset apply now goes straight through ModeCubit.applyPreset (mode→presets +
AutoHeatService restart with preset.settings) and PresetCubit.applyPreset
(selectedPresetId + lastUsed). The cross-write into ManualSettingsCubit that
previously overwrote system auto-mode settings is gone."
```

---

## Task 7: `PresetsTab._onDelete` — fallback при удалении активного пресета в presets-режиме

**Goal:** Если удаляется активный для user'а пресет И user в `HeatMode.presets`, перевести его в `manual heatLevel=0` и показать SnackBar.

**Files:**
- Modify: `lib/src/presentation/screens/presets/presets_tab.dart`

- [ ] **Step 1: Обновить `_onDelete`**

Заменить текущий метод:

```dart
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
```

на:

```dart
Future<void> _onDelete(Preset preset) async {
  final modeCubit = context.read<ModeCubit>();
  final presetCubit = context.read<PresetCubit>();

  final wasActiveForUser =
      presetCubit.state.selectedPresets[preset.userType]?.id == preset.id;
  final currentModeName = modeCubit.getModeByUser(preset.userType);
  final wasInPresetsMode = currentModeName == HeatMode.presets.name;

  await presetCubit.deletePreset(preset.id, preset.userType);
  if (!mounted) return;

  if (_editingPresetId == preset.id) {
    setState(() {
      _editingPresetId = null;
      _draftSettings = null;
    });
  }

  if (wasActiveForUser && wasInPresetsMode) {
    // Активный пресет удалён, пока user в presets-режиме — алгоритм остался бы
    // без источника settings (PresetCubit уже очистил selectedPresetId внутри
    // deletePreset). Явно откатываем в manual+0 и сообщаем.
    await modeCubit.setMode(preset.userType, HeatMode.manual.name);
    await modeCubit.setHeatLevel(preset.userType, 0);
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          'Активный пресет удалён — режим переключён в ручной',
          style: TextStyle(color: context.themeColors.textButtonSelected),
        ),
        backgroundColor: context.themeColors.primary,
      ),
    );
  }
}
```

- [ ] **Step 2: Добавить нужные импорты в `presets_tab.dart`**

Убедиться, что есть импорты:

```dart
import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/cubit/mode_cubit.dart';
import 'package:autoheat/src/extensions/context_extensions.dart';
```

(`app_enums.dart` — для `HeatMode.presets.name`, `mode_cubit.dart` — для `ModeCubit`, `context_extensions.dart` — для `themeColors`.)

- [ ] **Step 3: Прогнать analyzer и тесты**

```bash
flutter analyze lib/src/presentation/screens/presets/presets_tab.dart
flutter test
```

Expected: всё зелёное.

- [ ] **Step 4: Commit**

```bash
git add lib/src/presentation/screens/presets/presets_tab.dart
git commit -m "Fallback to manual heatLevel=0 when active preset is deleted

If the deleted preset is the user's active preset and the user is currently
in HeatMode.presets, ModeCubit switches to manual + level 0 and a SnackBar
notifies the user. Without this fallback the algorithm would keep running
against the deleted preset until the next mode change."
```

---

## Task 8: Удаление `ManualSettingsCubit` / `ManualSettingsService` / `ManualSettingsState`

**Goal:** Снести все ссылки на «системный держатель settings» — теперь TemperatureConstants берёт эту роль.

**Files:**
- Delete: `lib/src/cubit/manual_settings_cubit.dart`
- Delete: `lib/src/services/manual_settings_service.dart`
- Delete: `test/unit/manual_settings_cubit_test.dart`
- Modify: `lib/src/models/manual_settings.dart` (удалить `ManualSettingsState`).
- Modify: `lib/src/di/service_locator.dart`
- Modify: `lib/src/di/app_bloc_providers.dart`
- Modify: `lib/src/presentation/screens/presets/presets_tab.dart` (убрать вызов `initialize()` и импорт)
- Modify: `test/widget/settings_screen_test.dart`

- [ ] **Step 1: Удалить файлы**

```bash
rm lib/src/cubit/manual_settings_cubit.dart \
   lib/src/services/manual_settings_service.dart \
   test/unit/manual_settings_cubit_test.dart
```

- [ ] **Step 2: Обновить `manual_settings.dart` модель**

В `lib/src/models/manual_settings.dart` удалить класс `ManualSettingsState` и связанные comments в `MODULE_MAP` (`ManualSettingsState`, `ManualSettingsState.copyWith`). Остаются только `ManualHeatSettings`, `AutoHeatLevel`, JSON helpers.

Bump шапку:

```dart
//   PURPOSE: JSON-модели настроек авторежима: ManualHeatSettings + AutoHeatLevel.
//   SCOPE: ManualHeatSettings (durations+threshold), AutoHeatLevel, JSON contract.
//   DEPENDS: M-ENUMS, M-CONSTANTS-TEMPERATURE
//   LINKS: M-MANUAL-SETTINGS, M-PRESET, V-M-PRESET
//   ...
//   LAST_CHANGE: [v1.3.0 - Mode-source decoupling: ManualSettingsState класс удалён вместе с ManualSettingsCubit]
//   PREVIOUS_CHANGE: [v1.2.0 - Phase-4 Slice-6: ManualSettingsState.copyWith supports clearError]
```

Прогнать `flutter analyze` после правки и удалить unused imports, если ругается (в этом файле могут остаться импорты, нужные только для удалённого класса — обычно нет, кроме `equatable` если он не используется в `ManualHeatSettings`).

- [ ] **Step 3: Обновить `service_locator.dart`**

Удалить блоки:

```dart
import 'package:autoheat/src/cubit/manual_settings_cubit.dart';
import 'package:autoheat/src/services/manual_settings_service.dart';
```

```dart
_registerSingletonIfAbsent<ManualSettingsService>(
    () => ManualSettingsService(locator<SharedPreferences>()));
```

```dart
_registerSingletonIfAbsent<ManualSettingsCubit>(
    () => ManualSettingsCubit(locator<ManualSettingsService>()));
```

Также из конструктора `ModeCubit` уже убран `ManualSettingsService` в Task 4 — проверить, что это так.

- [ ] **Step 4: Обновить `app_bloc_providers.dart`**

Удалить:

```dart
import 'package:autoheat/src/cubit/manual_settings_cubit.dart';
```

```dart
BlocProvider<ManualSettingsCubit>(
    create: (context) => locator<ManualSettingsCubit>()),
```

- [ ] **Step 5: Обновить `PresetsTab`**

В `lib/src/presentation/screens/presets/presets_tab.dart`:

a) Удалить из `initState`:

```dart
context.read<ManualSettingsCubit>().initialize();
```

b) Удалить импорт:

```dart
import 'package:autoheat/src/cubit/manual_settings_cubit.dart';
```

c) Обновить шапку: вычеркнуть `M-MANUAL-SETTINGS` из `DEPENDS` (если присутствует).

```dart
//   DEPENDS: M-UI-PRESETS, M-PRESET, M-MODE, M-ENUMS, M-THEME
```

- [ ] **Step 6: Обновить `settings_screen_test.dart`**

В `test/widget/settings_screen_test.dart` удалить:

```dart
import 'package:autoheat/src/cubit/manual_settings_cubit.dart';
import 'package:autoheat/src/services/manual_settings_service.dart';
```

И harness:

```dart
final manualSettingsCubit = ManualSettingsCubit(ManualSettingsService(prefs));
addTearDown(manualSettingsCubit.close);
```

```dart
BlocProvider<ManualSettingsCubit>.value(value: manualSettingsCubit),
```

— все три строки удалить.

- [ ] **Step 7: Прогнать analyzer**

```bash
flutter analyze
```

Expected: `No issues found!`. Если ещё что-то ссылается на `ManualSettingsCubit`/`ManualSettingsService`/`ManualSettingsState` — поправить по тексту ошибки.

- [ ] **Step 8: Прогнать тесты**

```bash
flutter test
```

Expected: все зелёные. Один тест уйдёт из счёта (удалённый `manual_settings_cubit_test.dart` — около 10–15 сценариев); итоговое число тестов уменьшится.

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "Remove ManualSettingsCubit/Service/State and their wiring

Auto-mode settings are now system defaults from TemperatureConstants (already
encoded in AutoHeatService._getSequence fallback). Preset-mode settings come
directly from preset.settings. ManualSettingsCubit was the leak that crossed
those two — its removal locks in the decoupling.

- Delete lib/src/cubit/manual_settings_cubit.dart
- Delete lib/src/services/manual_settings_service.dart
- Delete test/unit/manual_settings_cubit_test.dart
- Remove ManualSettingsState class from lib/src/models/manual_settings.dart
- Drop ManualSettings* registrations from DI and BlocProviders
- Remove ManualSettingsCubit.initialize() from PresetsTab
- Update settings_screen_test harness to not inject the removed cubit"
```

---

## Task 9: Обновление `service_locator_test.dart` и регрессионный прогон

**Goal:** Зачистить хвост в DI-тесте, прогнать полный набор и `flutter analyze`.

**Files:**
- Modify: `test/unit/service_locator_test.dart`

- [ ] **Step 1: Найти упоминания `ManualSettings*` в тесте**

```bash
grep -n "ManualSettings" test/unit/service_locator_test.dart
```

- [ ] **Step 2: Удалить ассерты `expect(locator.isRegistered<ManualSettingsService>(), isTrue)` и аналогичные для `ManualSettingsCubit`**

Удалить каждую строку, которая ожидает регистрацию `ManualSettings*` в локаторе. Также удалить импорты этих типов.

- [ ] **Step 3: Если есть ассерт типа `ModeCubit(arg1, arg2, arg3, arg4)`-aware — обновить**

В service_locator_test обычно проверяют, что `locator<ModeCubit>()` создаётся; явных аргументов не сверяют. Если есть сценарий, который проверяет `ModeCubit.constructor.parameters.length` или что-то похожее — убрать (это уже smell).

- [ ] **Step 4: Прогнать analyzer и тесты**

```bash
flutter analyze
flutter test
```

Expected: оба зелёные.

- [ ] **Step 5: Commit**

```bash
git add test/unit/service_locator_test.dart
git commit -m "Drop ManualSettings* expectations from service_locator_test"
```

---

## Task 10: Manual smoke на голове

App уже работает на head unit; hot-reload должен подхватить большую часть изменений. Если ведёт себя странно — hot-restart (`R` в терминале `flutter run`).

- [ ] **Step 1: Холодный старт после `manual` режима**

Уничтожить процесс и запустить заново. У обоих сидений `HeatMode.manual`, `heatLevel = 0`. Никаких неожиданных подогревов.

- [ ] **Step 2: Переключить driver в auto через сегмент**

Тап «Авто» → AutoHeatService должен запуститься с дефолтами из `TemperatureConstants` (соответствующими текущей температуре). Уровень на сиденье отражает шаг расписания.

- [ ] **Step 3: Сохранить и применить пользовательский пресет**

Создать пресет с явно отличающимися от системных настройками (например `threshold = 12 °C`, durations `7/8/9`). Применить (▶). Проверить:

- сегмент сменился на «Пресеты»;
- расписание подогрева начинается мгновенно (immediateHeatLevelFuture).

- [ ] **Step 4: Переключить в «Авто» обратно**

Сегмент «Авто». Расписание должно идти по системным дефолтам — duration'ы соответствуют `TemperatureConstants.getHeatSequence` (5/4/4 и т.п., смотри константы). НЕ duration'ы только что применённого пресета.

- [ ] **Step 5: Тап «Пресеты» когда у passenger нет активного пресета**

Переключиться на passenger (если возможно через UI) или удалить selectedPreset у driver. Тап «Пресеты» — должен открыться таб «Пресеты», сегмент НЕ защёлкивается на «Пресеты», остаётся на текущем.

- [ ] **Step 6: Удаление активного пресета в presets-режиме**

Применить пресет → удалить эту же запись из списка справа. Проверить:

- сиденье переключается в manual heatLevel=0;
- SnackBar «Активный пресет удалён — режим переключён в ручной».

- [ ] **Step 7: Cold-start с persisted presets-режимом**

Применить пресет. Закрыть приложение. Снова открыть. У user'а должен сохраниться `HeatMode.presets`, AutoHeatService подняться с этим preset settings.

- [ ] **Step 8: Cold-start с (presets, selectedPresetId = удалённый)**

Применить пресет → закрыть приложение → удалить запись из SharedPreferences вручную (через debugger) — либо альтернативно: применить → закрыть → переустановить app без миграции state? Если такой путь слишком сложен на голове, заменить шаг на проверку только через unit-тест из Task 3 (scenario-N+4) и записать в acceptance: «edge case unit-only».

- [ ] **Step 9: Финальный analyzer + tests**

```bash
flutter analyze
flutter test
```

Expected: `No issues found!`, все зелёные.

---

## Task 11: Обновление GRACE-артефактов

**Files:**
- Modify: `docs/knowledge-graph.xml`
- Modify: `docs/development-plan.xml`
- Modify: `docs/verification-plan.xml`

- [ ] **Step 1: `knowledge-graph.xml`**

a) Удалить элемент `<M-MANUAL-SETTINGS …>` целиком — модуль ушёл.

b) Найти `<M-MODE …>` элемент: обновить `depends`:

```xml
<depends>M-HVAC, M-AUTO-HEAT, M-PRESET, M-ENUMS, M-LOGGER</depends>
```

(было `M-MANUAL-SETTINGS` — убрать.)

c) `<M-PRESET …>` — в `MODULE_MAP` или annotations удалить упоминания `heatMode`/`heatLevel` snapshot.

d) `<M-UI-PRESETS …>` — `depends` оставить как есть (`M-PRESET, M-MANUAL-SETTINGS, M-MODE, M-ENUMS, M-THEME` → убрать `M-MANUAL-SETTINGS`).

e) Удалить или поправить `CrossLink`'и, которые ссылаются на `M-MANUAL-SETTINGS`. Конкретно:

```bash
grep -n "M-MANUAL-SETTINGS" docs/knowledge-graph.xml
```

— по каждой найденной строке: либо удалить (если M-MANUAL-SETTINGS — endpoint), либо обновить (если использовалось в комбинации).

- [ ] **Step 2: `development-plan.xml`**

a) Удалить `<M-MANUAL-SETTINGS …>` элемент целиком.

b) Найти `<M-MODE …>` элемент: убрать `M-MANUAL-SETTINGS` из `depends` и `<interface>` секции (если был экспорт `applyPresetSettings` или похожий).

c) Добавить в раздел `<ImplementationOrder>` новый Phase-6 entry:

```xml
<Phase-6 name="Mode-source decoupling" status="done">
  <goal>Развязать HeatMode.auto и HeatMode.presets так, чтобы apply пресета не подменял системные настройки авторежима.</goal>
  <step-1 module="M-PRESET" status="done" verification="V-M-PRESET">Удалить snapshot-поля Preset.heatMode/heatLevel и обновить createPresetFromCurrentSettings + PresetCubit.savePreset signatures.</step-1>
  <step-2 module="M-MODE" status="done" verification="V-M-MODE">ModeCubit теряет ManualSettingsService dep; applyPreset всегда HeatMode.presets с preset.settings; setMode(auto) без settings → TemperatureConstants; setMode(presets, settings:) с переданными; _initializeHeatModes резолвит preset-settings через PresetService.</step-2>
  <step-3 module="M-UI-HEAT" status="done" verification="V-M-UI-HEAT">ModeToggler сегмент «Пресеты» вызывает onPresetsSegmentTapped callback; AppContent либо навигирует на Presets-вкладку, либо применяет активный пресет.</step-3>
  <step-4 module="M-UI-APP" status="done" verification="V-M-UI-APP">AppContent._applyPreset больше не пишет в ManualSettingsCubit.</step-4>
  <step-5 module="M-UI-PRESETS" status="done" verification="V-M-UI-PRESETS">PresetsTab._onDelete fallback на manual+0 при удалении активного пресета в presets-режиме.</step-5>
  <step-6 module="M-MANUAL-SETTINGS" status="removed">Удалить ManualSettingsCubit, ManualSettingsService, ManualSettingsState и связанные DI/Provider регистрации.</step-6>
</Phase-6>
```

- [ ] **Step 3: `verification-plan.xml`**

a) Удалить `<V-M-MANUAL-SETTINGS …>` элемент целиком.

b) Обновить `<V-M-MODE …>` scenarios:

- удалить сценарии, проверявшие `preset.heatMode`/`heatLevel` snapshot;
- добавить:
  - «applyPreset(preset) всегда выставляет HeatMode.presets и стартует AutoHeatService с preset.settings»;
  - «setMode(auto) стартует AutoHeatService без settings → algorithm читает TemperatureConstants»;
  - «_initializeHeatModes для HeatMode.presets восстанавливает preset.settings через PresetService»;
  - «_initializeHeatModes для HeatMode.presets с отсутствующим selectedPresetId → fallback на manual heatLevel=0».

c) `<V-M-PRESET …>` — убрать упоминания `heatMode`/`heatLevel` в required-trace-assertions.

d) `<V-M-UI-APP …>` — добавить сценарий «_applyPreset делегирует только ModeCubit.applyPreset + PresetCubit.applyPreset, не трогает ManualSettings*».

e) `<V-M-UI-PRESETS …>` — добавить сценарий: «Удаление активного пресета в presets-режиме переводит user в manual heatLevel=0 и показывает SnackBar».

f) `<V-M-UI-HEAT …>` (если есть) — добавить сценарий: «Тап на сегмент Пресеты без активного пресета вызывает onPresetsSegmentTapped и НЕ меняет mode-state».

- [ ] **Step 4: `operational-packets.xml`**

```bash
grep -n "M-MANUAL-SETTINGS\|ManualSettings" docs/operational-packets.xml 2>/dev/null || echo "no file or no matches"
```

Если файл существует и есть совпадения — поправить или удалить ссылки.

- [ ] **Step 5: Commit GRACE artefacts**

```bash
git add docs/knowledge-graph.xml docs/development-plan.xml docs/verification-plan.xml
[ -f docs/operational-packets.xml ] && git add docs/operational-packets.xml
git commit -m "Update GRACE artifacts for mode-source decoupling

- Remove M-MANUAL-SETTINGS module from knowledge-graph and development-plan
- Update M-MODE depends (drop M-MANUAL-SETTINGS); refresh V-M-MODE scenarios
- Refresh V-M-PRESET to remove heatMode/heatLevel expectations
- Add V-M-UI-APP, V-M-UI-PRESETS, V-M-UI-HEAT scenarios for new flows
- Add Phase-6 'Mode-source decoupling' to development-plan"
```

---

## Task 12: Финальный коммит + спека Implemented

- [ ] **Step 1: Отметить спеку как implemented**

В `docs/superpowers/specs/2026-05-24-mode-source-decoupling-design.md` заменить заголовок Status:

```markdown
> **Status:** Implemented 2026-05-24 (plan `docs/superpowers/plans/2026-05-24-mode-source-decoupling.md`).
```

- [ ] **Step 2: Commit**

```bash
git add docs/superpowers/specs/2026-05-24-mode-source-decoupling-design.md
git commit -m "Mark mode-source decoupling spec as implemented"
```

- [ ] **Step 3: Финальный прогон**

```bash
flutter analyze
flutter test
git log --oneline master..HEAD
```

Expected: `No issues found!`, все тесты зелёные, в `git log` — серия коммитов по задачам этого плана.

---

## Self-review

**Spec coverage:**

- Decision 1 (settings source per HeatMode) → Tasks 3, 4.
- Decision 2 (ModeCubit API changes — drop ManualSettingsService, applyPreset emits presets, _initializeHeatModes via PresetService) → Tasks 4.
- Decision 3 (Preset model loses heatMode/heatLevel + cascading signatures) → Tasks 1, 2.
- Decision 4 (Removed modules — ManualSettingsCubit/Service/State + DI + Provider + PresetsTab init + SharedPreferences key abandonment) → Task 8.
- Decision 5 (Apply / delete behavior — drop ManualSettings call; delete-active fallback + SnackBar) → Tasks 6, 7.
- Decision 6 (ModeToggler presets-segment guard + AppContent navigation) → Task 5.
- Decision 7 (Verification updates) → Task 11.
- Test changes table (manual_settings_cubit_test delete; mode_cubit/preset_* fixture updates; service_locator_test cleanup; settings_screen_test harness) → Tasks 1, 3, 8, 9.

Open Q1–Q4 → закрыты в «Tactical decisions» секции выше; их применение в коде распределено по Tasks 4, 5, 6.

**Placeholder scan:** прошёл. Нет «TBD», «implement later», «similar to Task N». Каждая задача содержит явные блоки кода и точные команды.

**Type consistency:** проверил —
- `setMode(UserType, String, {ManualHeatSettings? settings})` используется одинаково в Task 4 (определение) и Task 5 (не вызывается извне с settings — UI пользуется `applyPreset` для presets-входа).
- `applyPreset(Preset preset)` без settings-параметра — определение Task 4, использование Task 5+6.
- `onPresetsSegmentTapped: VoidCallback` в Task 5 — без аргументов; передаётся из `HeatScreen` через `() => onPresetsSegmentTapped(UserType.driver)` (wrapper, аналог для passenger). `AppContent._onPresetsSegmentTapped(UserType user)` — с аргументом. Имена согласованы.
- `_resolveSelectedPresetSettings(UserType)` — приватный helper Task 4, нигде не вызывается извне.
