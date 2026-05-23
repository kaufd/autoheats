# Phase-4 Slice 3 Cabin Temperature Design

## Goal
Fix FA-003 with the correct architectural boundary: HVAC is the source of cabin-temperature data, AutoHeatService consumes it for runtime scheduling, and UI consumes it through a dedicated CabinTemperatureCubit.

## Decisions

1. Replace the single `HvacService.onCabinTemperatureChanged` callback with a multi-listener API:
   - `addCabinTemperatureListener(listener, {emitCurrent})`
   - `removeCabinTemperatureListener(listener)`
   - `lastCabinTemperature`
2. `HvacService` caches the last cabin temperature. Sensor events and explicit `getCabinTemperature()` reads both update the cache and notify listeners.
3. `AutoHeatService` subscribes to `HvacService` through the listener API. It no longer owns the only callback slot.
4. `AutoHeatService.seedCurrentTemperatureFromHvac()` performs an initial read after subscription so auto mode can start without waiting for the next sensor event.
5. Add `CabinTemperatureCubit` as the UI state projection for cabin temperature. It subscribes to `HvacService`, emits initial cached/read temperature, and unsubscribes on close.
6. `CabinTemperatureDisplay` reads `CabinTemperatureCubit`, not `ModeCubit` or `AutoHeatService` singleton state.
7. `ModeCubit` remains responsible for mode/level/persistence/HVAC commands only. It initializes AutoHeatService and seeds initial temperature, but does not expose cabin-temperature UI state.

## Data flow

### Runtime sensor event

`AndroidAutomotivePlugin.onHvacChangeEventCallback` → `HvacService._handleCabinTemperature` → cache + listeners →
- `AutoHeatService` recalculates active auto schedules.
- `CabinTemperatureCubit` emits UI state.

### Initial temperature

`ModeCubit._initialize` → `AutoHeatService.initialize(hvac)` → `AutoHeatService.seedCurrentTemperatureFromHvac()` → `HvacService.getCabinTemperature()` → cache + listeners → AutoHeatService current temperature.

`CabinTemperatureCubit` subscribes with `emitCurrent: true`; if no cached value exists, it reads `HvacService.getCabinTemperature()` itself.

## Error handling

- `HvacService.getCabinTemperature()` keeps the existing safe fallback (`20.0`) and now publishes that fallback as cached state.
- A throwing temperature listener must not prevent other listeners from receiving the event; `HvacService` logs and continues.
- Cubits guard emits after `close()`.

## Testing

- `test/unit/hvac_service_test.dart`: multi-listener fan-out and removal; getCabinTemperature read updates cache/listeners.
- `test/unit/auto_heat_service_test.dart`: initialize + seed from programmed HVAC temperature starts schedule without sensor event.
- `test/unit/cabin_temperature_cubit_test.dart`: initial read, cached emit, sensor update, unsubscribe on close.
- `test/widget/cabin_temperature_display_test.dart`: display updates when CabinTemperatureCubit receives an HVAC event.
- Existing `ModeCubit` tests cover auto startup through initial temperature and no longer depend on temperature UI state.

## Scope exclusions

- FA-005 sensor-noise/range restart guard remains separate.
- Background lifecycle smoke (FA-006) remains separate.
- No external logging/state libraries are added.
