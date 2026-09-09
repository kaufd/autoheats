# Архитектура AutoHeat

```text
MainActivity / XML tabs
        │ команды и отображение
        ▼
SeatHeatService ── CarHvacProbe ── android.car.* / CarService
        │                 │
        │                 └── температура, уровни сидений, ignition
        ├── AutoHeatEngine ── уровни и расписания
        ├── PresetStore ───── SharedPreferences
        ├── IgnitionSession ─ выключение после ON → ACC/OFF
        └── LogBuffer ─────── кольцевой диагностический лог
```

`SeatHeatService` — владелец соединения с автомобилем и foreground-service; Activity не разрывает его при закрытии. `BootReceiver` поднимает сервис после обычной и vendor-перезагрузки MediaTek. `HeatAccessibilityService` помогает сохранять жизненный цикл после сна головы.

`CarHvacProbe` подписывается на HVAC и ignition независимо: отказ одного канала не отменяет другой. Температура вычисляется как `(raw - 84) / 2`; при `raw < 0` значение не публикуется. Переходы ignition `LOCK`, `OFF`, `ACC` и `UNDEFINED` выключают оба сиденья лишь после ранее полученного `ON`; `START` игнорируется, чтобы не гасить подогрев при запуске двигателя.

Все runtime-ресурсы находятся в `app/src/main/`, unit-тесты — в `app/src/test/`. Сборка, lint и тесты выполняются одним Gradle-проектом из корня: `./gradlew test lint assembleRelease`.
