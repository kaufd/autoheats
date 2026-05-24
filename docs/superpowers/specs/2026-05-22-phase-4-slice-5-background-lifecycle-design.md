# Phase-4 Slice 5 Background Lifecycle Design

## Goal
Fix FA-006 by moving background-service runtime behavior behind a testable controller and hardening stop, ignition OFF, and restart-backoff behavior.

## Decisions

1. Keep `onStart(ServiceInstance)` as the flutter_background_service entry-point adapter.
2. Add `BackgroundRuntimeController` under `lib/src/services/` as part of `M-BACKGROUND`, not a new public feature module.
3. Use small ports for testability:
   - `BackgroundServicePort`: `on(method)`, `stopSelf()`, `setForegroundNotificationInfo(...)`.
   - `BackgroundModePort`: `setHeatLevel(UserType, int)`.
4. The real adapters wrap `ServiceInstance`/`AndroidServiceInstance` and `ModeCubit`.
5. `BackgroundRuntimeController` owns:
   - stop command subscription (`stopService` → seat shutdown → `stopSelf()`);
   - ignition handling (`IGNITION_STATE_OFF` → awaited driver/passenger `setHeatLevel(0)`);
   - restart-backoff policy after startup failure;
   - per-seat failure logging during shutdown.
6. `background_service.dart` keeps plugin/DI setup and delegates runtime decisions to the controller.
7. Retry contract: startup failure schedules a retry callback while attempts remain; after max attempts the service fail-stops via `stopSelf()`.

## Data flow

### Stop command
`FlutterBackgroundService().invoke('stopService')` → background `ServiceInstance.on('stopService')` → `BackgroundRuntimeController.stopService()` → awaited seat shutdown → `ServiceInstance.stopSelf()`.

### Ignition OFF
`AndroidAutomotivePlugin.onCarSensorEventCallback` → `BackgroundRuntimeController.handleIgnition()` → awaited driver/passenger `setHeatLevel(0)`.

### Startup failure
`onStart` catches runtime startup failure → `BackgroundRuntimeController.handleStartFailure(...)` → notification update + delayed retry callback, or `stopSelf()` at max attempts.

## Error handling

- Each seat shutdown failure is logged with `userType` and trigger; the second seat is still attempted.
- Stop command always attempts `stopSelf()`, even if seat shutdown fails.
- Malformed ignition events are caught/logged and do not crash the isolate.
- Restart attempts are bounded by `_maxRestartAttempts`.

## Testing

- Unit tests for `BackgroundRuntimeController` with fake ports:
  - stop command invokes seat shutdown and stopSelf;
  - ignition OFF awaits driver/passenger level 0;
  - malformed ignition event is ignored;
  - restart-backoff schedules retry below max;
  - max attempts calls stopSelf.
- Existing app-wide tests/analyze remain required.
- Manual head-unit smoke remains mandatory before declaring background lifecycle validated on hardware.

## Scope exclusions

- FA-007 plugin fire-and-forget remains separate.
- FA-008 DI idempotency remains separate.
- No change to Android manifest or foreground notification channel IDs.
