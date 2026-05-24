# GRACE Framework - Project Engineering Protocol

## Keywords
flutter, dart, android-automotive, changan, hvac, seat-heating, head-unit, foreground-service, bloc, cubit

## Annotation
Flutter-приложение для автоматического управления подогревом сидений в автомобиле Changan. Работает на головном устройстве под Android Automotive OS (UNI-S, CS55 Plus, MediaTek MT8666 / arm64), общается с HVAC и датчиками через локальный path-плагин `packages/android_automotive_plugin/` (взят с GitHub `abuharsky/changan_car_flutter_library`, переложен в `packages/` ради удобства локальной модификации), переключает уровни подогрева по датчику температуры салона или по пользовательским режимам/пресетам, продолжает работать в фоне через foreground-service, выключает сиденья по ignition OFF. Проект — переработка `autoheat_old`, текущая версия 1.0.0. Имеет скрытый debug-режим (long-press по индикатору температуры салона) с in-memory логами и инжектором температуры в `AutoHeatService`. Релизы публикуются автоматически по push'у тега `v*` через `.github/workflows/release.yml`. См. `docs/requirements.xml`, `docs/technology.xml`, `CLAUDE.md`, `CHANGELOG.md` и `README.md` для расширенного контекста.

## Core Principles

### 1. Never Write Code Without a Contract
Before generating or editing any module, create or update its MODULE_CONTRACT with PURPOSE, SCOPE, INPUTS, and OUTPUTS. The contract is the source of truth. Code implements the contract, not the other way around.

### 2. Semantic Markup Is Load-Bearing Structure
Markers like `// START_BLOCK_<NAME>` and `// END_BLOCK_<NAME>` are navigation anchors, not documentation. They must be:
- uniquely named
- paired
- proportionally sized so one block fits inside an LLM working window

### 3. Knowledge Graph Is Always Current
`docs/knowledge-graph.xml` is the project map. When you add a module, move a module, rename exports, or add dependencies, update the graph so future agents can navigate deterministically.

### 4. Verification Is a First-Class Artifact
Testing, traces, and log anchors are designed before large execution waves. `docs/verification-plan.xml` is part of the architecture, not an afterthought. Logs are evidence. Tests are executable contracts.

### 5. Top-Down Synthesis
Code generation follows:
`RequirementsAnalysis -> TechnologyStack -> DevelopmentPlan -> VerificationPlan -> Code + Tests`

Never jump straight to code when requirements, architecture, or verification intent are still unclear.

### 6. Governed Autonomy
Agents have freedom in HOW to implement, but not in WHAT to build. Contracts, plans, graph references, and verification requirements define the allowed space.

## Grep-First Navigation

Use shared docs and semantic anchors as the primary navigation surface. Prefer grep and exact-text lookup before broad prose reading.

Navigation order:

1. Shared/public truth: `docs/knowledge-graph.xml`, `docs/development-plan.xml`, `docs/verification-plan.xml`
2. File-local/private truth: `MODULE_CONTRACT`, `MODULE_MAP`, `CHANGE_SUMMARY`, function contracts, semantic blocks
3. Full file reads only after the target module, file, or block is narrowed

Canonical search anchors:

- `START_MODULE_CONTRACT` / `END_MODULE_CONTRACT`
- `START_MODULE_MAP` / `END_MODULE_MAP`
- `START_CONTRACT:` / `END_CONTRACT:`
- `START_BLOCK_` / `END_BLOCK_`
- `START_CHANGE_SUMMARY` / `END_CHANGE_SUMMARY`
- `LINKS:` for graph-linked references
- `M-` for module IDs
- `V-M-` for verification IDs
- `CrossLink` for graph edges

Canonical grep-stable naming rules:

- Module IDs use exact uppercase kebab form: `M-<TOKEN>` or `M-<TOKEN>-<TOKEN>` (`M-HVAC`, `M-AUTO-HEAT`)
- Verification IDs use exact derived form: `V-M-<MODULE-SUFFIX>` (`V-M-HVAC`, `V-M-AUTO-HEAT`)
- Module contract field names stay exact: `PURPOSE`, `SCOPE`, `DEPENDS`, `LINKS`, `ROLE`, `MAP_MODE`
- Function contract field names stay exact: `PURPOSE`, `INPUTS`, `OUTPUTS`, `SIDE_EFFECTS`, `LINKS`
- Semantic block names use uppercase snake form after the prefix: `START_BLOCK_SET_SEAT_HEAT_LEVEL`
- `LINKS:` values should prefer exact IDs or canonical annotation tags instead of prose references: `M-*`, `V-M-*`, `fn-*`, `type-*`, `class-*`, `export-*`, `const-*`
- Graph edges use the exact `CrossLink from="..." to="..." relation="..."` shape; do not invent alternate attribute names

Canonical search recipes:

- Find the target module record: search `M-<ID>` in `docs/development-plan.xml` and `docs/knowledge-graph.xml`
- Find verification for a module: search `V-M-<ID>` or the module ID in `docs/verification-plan.xml`
- Find implementation files tied to graph context: search `LINKS:` plus the module ID in `lib/` and `test/`
- Find file-local contracts quickly: search `START_MODULE_CONTRACT` or `START_CONTRACT:`
- Find important logic slices: search `START_BLOCK_`
- Find recent local rationale: search `START_CHANGE_SUMMARY`

AI-friendly documentation rule:

- do not restate code in prose when exact anchors already exist
- record only non-obvious intent, invariants, hazards, and search hints
- if a fact can be maintained as code, XML, contract markup, or a stable anchor, keep it there instead of duplicating it in Markdown

## Semantic Markup Reference

### Module Level
```dart
// FILE: lib/src/services/hvac_service.dart
// VERSION: 1.0.0
// START_MODULE_CONTRACT
//   PURPOSE: Централизованная обёртка над AndroidAutomotivePlugin для UI и AutoHeatService.
//   SCOPE: setSeatHeatLevel, getCabinTemperature, onCabinTemperatureChanged callback.
//   DEPENDS: M-PLUGIN, M-ENUMS
//   LINKS: M-HVAC, V-M-HVAC
//   ROLE: RUNTIME
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   HvacService - singleton, обёртка плагина
//   initialize - ленивый connect()
//   setSeatHeatLevel(UserType, int) - запись уровня через CarHvacManager
//   getCabinTemperature - чтение текущей температуры салона (°C)
//   onCabinTemperatureChanged - callback изменения температуры
// END_MODULE_MAP
```

### Function or Component Level
Place START_CONTRACT/END_CONTRACT above function signature and docstrings/comments.
```dart
// START_CONTRACT: setSeatHeatLevel
//   PURPOSE: Записать уровень подогрева через CarHvacManager.
//   INPUTS: { userType: UserType, level: int (0..3) }
//   OUTPUTS: { Future<void> - завершение записи }
//   SIDE_EFFECTS: Вызов нативного слоя android.car.* через AndroidAutomotivePlugin.
//   LINKS: M-HVAC, M-PLUGIN, V-M-HVAC, DF-SET-HEAT
// END_CONTRACT: setSeatHeatLevel
```

### Code Block Level
```dart
// START_BLOCK_SET_SEAT_HEAT_LEVEL
// ... код установки уровня ...
// END_BLOCK_SET_SEAT_HEAT_LEVEL
```

### Change Tracking
```dart
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v0.2.0 - GRACE-инициализация, добавлены MODULE_CONTRACT и MAP]
// END_CHANGE_SUMMARY
```

### Optional Lint Semantics

Use `ROLE` and `MAP_MODE` only when the file should be linted differently from a normal runtime module.

- `RUNTIME` + `EXPORTS`: обычные сервисы (`hvac_service.dart`, `auto_heat_service.dart`)
- `TEST` + `LOCALS`: будущие тесты в `test/`
- `BARREL` + `SUMMARY`: re-export файлы (если появятся)
- `CONFIG` + `NONE`: `pubspec.yaml`, `analysis_options.yaml`, gradle-конфиги
- `TYPES` + `EXPORTS`: `app_enums.dart`, модели `lib/src/models/`
- `SCRIPT` + `LOCALS`: CLI/bootstrap скрипты (нет в проекте сейчас)

## Logging and Trace Convention

All important logs must point back to semantic blocks:
```dart
print('[HvacService][setSeatHeatLevel][BLOCK_SET_SEAT_HEAT_LEVEL] level=$level userType=${userType.name}');
```

Целевой формат (после Phase-3 migration на logger):
```
[ModuleName][functionName][BLOCK_NAME] message | userType=... | level=...
```

Rules:
- prefer structured fields over prose-heavy log lines (`userType`, `heatLevel`, `temperatureCelsius`)
- redact secrets and high-risk payloads (в этом проекте секретов нет; не логировать raw `CarPropertyValue.value` без префикса `[debug]`)
- treat missing log anchors on critical branches as a verification defect
- update tests when log markers change intentionally

## Verification Conventions

`docs/verification-plan.xml` is the project-wide verification contract. Keep it current when module scope, test files, commands, critical log markers, or gate expectations change. Use `docs/operational-packets.xml` as the canonical schema for execution packets, graph deltas, verification deltas, and failure handoff packets.

Testing rules:
- deterministic assertions first (`flutter_test`)
- trace или log-assertions, когда важен порядок событий (особенно для AutoHeatService и BackgroundService)
- test files may also carry MODULE_CONTRACT, MODULE_MAP, semantic blocks, and CHANGE_SUMMARY when they are substantial
- module-local tests should stay close to the module they verify (`test/unit/<module>_test.dart`)
- wave-level and phase-level checks should be explicit in the verification plan
- для авторежима использовать `package:fake_async/fake_async.dart`, чтобы тестировать Timer-расписания детерминированно

## File Structure
```
docs/
  requirements.xml         - Use cases, actors, constraints, risks, open questions
  technology.xml           - Flutter/Dart стек, тестирование, observability, autonomy policy
  development-plan.xml     - Модули (M-*), data flows (DF-*), фазы, ExecutionPolicy
  verification-plan.xml    - Тесты, log markers, scenarios, phase gates
  knowledge-graph.xml      - Граф модулей + CrossLinks
  operational-packets.xml  - Шаблоны ExecutionPacket / Delta / FailurePacket
lib/
  main.dart                - точка входа (M-MAIN)
  src/
    app_enums.dart         - M-ENUMS
    cubit/                 - M-MODE, M-CABIN-TEMPERATURE, M-PRESET, M-SETTINGS
                            (ManualSettingsCubit удалён в рамках mode-source decoupling)
    di/                    - M-DI, M-BLOC-PROVIDERS
    config/                - темы, цвета, типографика
    constants/             - M-CONSTANTS-TEMPERATURE
    models/                - Preset, ManualHeatSettings (+ codegen)
    services/              - M-HVAC (in-flight initialize guard), M-AUTO-HEAT (plan-key guard),
                            M-BACKGROUND, M-BACKGROUND-RUNTIME (ignition OFF + retry-backoff),
                            M-ACCESSIBILITY, M-MODE, M-PRESET, M-SETTINGS
    utils/                 - M-LOGGER (Logger + LogRingBuffer для debug UI)
    presentation/          - M-UI-APP, M-UI-HEAT, M-UI-PRESETS, M-UI-SETTINGS, M-UI-DEBUG, M-THEME
      screens/debug/       - LogsScreen с sidebar-инжектором температуры
packages/
  android_automotive_plugin/ - M-PLUGIN (path-плагин; исходник с GitHub abuharsky/changan_car_flutter_library)
test/
  unit/                    - модульные тесты сервисов / кубитов / моделей
  widget/                  - widget-тесты UI
  scenarios/walkthrough_log_test.dart - log-driven сценарии auto/preset/переключения
  _helpers/                - FakeHvacService, FakePlugin, LoggerTestSink
.github/
  workflows/release.yml    - CI: триггер на push тега v*, build APK, GitHub Release
CHANGELOG.md               - Keep a Changelog формат; секция [X.Y.Z] = body GitHub Release
README.md                  - публичное описание проекта (русский)
ARCHITECTURE.md            - дополнительные диаграммы потоков
CLAUDE.md                  - расширенный человекочитаемый контекст
```

## Documentation Artifacts - Unique Tag Convention

In `docs/*.xml`, repeated entities must use their unique ID as the XML tag name instead of a generic tag with an `ID` attribute. This reduces closing-tag ambiguity and gives LLMs stronger anchors.

### Tag naming conventions

| Entity type | Anti-pattern | Correct (unique tags) |
|---|---|---|
| Module | `<Module ID="M-HVAC">...</Module>` | `<M-HVAC NAME="HvacService" TYPE="CORE_LOGIC">...</M-HVAC>` |
| Verification module | `<Verification ID="V-M-HVAC">...</Verification>` | `<V-M-HVAC MODULE="M-HVAC">...</V-M-HVAC>` |
| Phase | `<Phase number="1">...</Phase>` | `<Phase-1 name="Foundation">...</Phase-1>` |
| Flow | `<Flow ID="DF-SET-HEAT">...</Flow>` | `<DF-SET-HEAT NAME="...">...</DF-SET-HEAT>` |
| Use case | `<UseCase ID="UC-001">...</UseCase>` | `<UC-001>...</UC-001>` |
| Step | `<step order="1">...</step>` | `<step-1>...</step-1>` |
| Export | `<export name="setSeatHeatLevel" .../>` | `<export-setSeatHeatLevel .../>` |
| Function | `<function name="getCabinTemperature" .../>` | `<fn-getCabinTemperature .../>` |
| Type | `<type name="HeatSequence" .../>` | `<type-HeatSequence .../>` |
| Class | `<class name="HvacService" .../>` | `<class-HvacService .../>` |

### What NOT to change
- `CrossLink` tags stay self-closing
- single-use structural wrappers like `<contract>`, `<inputs>`, `<outputs>`, `<annotations>`, `<test-files>`, `<module-checks>`, and `<phase-gates>` stay generic
- code-level markup already uses unique names and stays as-is

## Rules for Modifications

1. Read the MODULE_CONTRACT before editing any file.
2. After editing source or test files, update MODULE_MAP in a way that matches the file's role and map mode.
3. After adding or removing modules, update `docs/knowledge-graph.xml`.
4. After changing test files, commands, critical scenarios, or log markers, update `docs/verification-plan.xml`.
5. After fixing bugs, add a CHANGE_SUMMARY entry and strengthen nearby verification if the old evidence was weak.
6. Never remove semantic markup anchors unless the structure is intentionally replaced with better anchors.
7. **Project-specific:** при изменении `setupServiceLocator` всегда обновлять и UI-, и background-onStart-путь — изолят свой DI.
8. **Project-specific:** при правке HVAC-маппингов температуры или property IDs обновлять `M-PLUGIN`, `M-HVAC`, `M-CONSTANTS-TEMPERATURE` и пересмотреть `V-M-*` сценарии.
9. **Project-specific:** ключи `SharedPreferences` и `enum.name` (`HeatMode`, `UserType`) — стабильный публичный контракт, переименование требует миграции.
10. **Project-specific:** `AndroidAutomotivePlugin` инстанцируется только в `HvacService`; второй конструктор перезаписывает `MethodCallHandler` канала. Если нужен plugin instance в другом месте (например, в `accessibility_service`) — брать через `locator<HvacService>().androidAutomotivePlugin`.
11. **Project-specific:** новые debug-affordances для тестирования (long-press toggles, диагностические инжекторы) должны при выключении сбрасывать свои side-effects, чтобы не влиять на реальную работу — см. `CabinTemperatureDisplay._toggleDebugMode` (`HvacService.getCabinTemperature` восстанавливает реальное значение, `LogRingBuffer.clear()` освобождает память).
12. **Project-specific:** релиз создаётся автоматически push'ом тега `v*`. Перед `git tag`: бамп `version` в `pubspec.yaml` + новая секция `## [X.Y.Z]` в `CHANGELOG.md` (Keep a Changelog формат). Workflow `.github/workflows/release.yml` вырезает секцию по точному заголовку `## [VERSION]` и кладёт в body Release; APK `AutoHeat-v3.apk` собирается под arm64 и прикрепляется как asset.
