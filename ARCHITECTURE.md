# Архитектура AutoHeat

```text
MainActivity / XML tabs
        ├── AppUpdateController ── GitHub Releases / DownloadManager / установщик APK
        │
        │ команды и отображение
        ▼
SeatHeatService ── CarHvacProbe ── android.car.* / CarService
        │                 │
        │                 └── температура, уровни сидений, ignition
        ├── ModelHeatDriver ── авто: такт 15 с, политика, подтверждения и повторы
        │       ├── ModelShadow → SeatObserver / SeatThermostat / SeatThermalModel
        │       └── AutoHeatEngine ── расписания пресетов
        ├── PresetStore ───── SharedPreferences
        ├── IgnitionSession ─ выключение после ON → ACC/OFF
        └── LogBuffer ─────── кольцевой диагностический лог
```

`SeatHeatService` — владелец соединения с автомобилем и foreground-service; Activity не разрывает его при закрытии. `BootReceiver` поднимает сервис после обычной и vendor-перезагрузки MediaTek. `HeatAccessibilityService` помогает сохранять жизненный цикл после сна головы.

`CarHvacProbe` подписывается на HVAC и ignition независимо: отказ одного канала не отменяет другой. Температура вычисляется как `(raw - 84) / 2`; при `raw < 0` значение не публикуется. Переходы ignition `LOCK`, `OFF`, `ACC` и `UNDEFINED` выключают оба сиденья лишь после ранее полученного `ON`; `START` игнорируется, чтобы не гасить подогрев при запуске двигателя.

Все runtime-ресурсы находятся в `app/src/main/`, unit-тесты — в `app/src/test/`. Сборка, lint и тесты выполняются одним Gradle-проектом из корня: `./gradlew test lint assembleRelease`.

Модель оценивает оба сиденья во всех режимах. `ModelHeatDriver` применяет решения
только к запущенным авто-сиденьям и при известном зажигании ON. Путь команды:
`updateModel → driver.apply → setSeatHeat → подтверждённые levels → sampler.record`.
Повторный вход из записи CSV обновляет наблюдателя, но не отправляет вложенную
команду. Обычный выбор — раз в минуту, при подтверждённой смене уровня план
пересчитывается с новой задержкой повышения. Прогноз перегрева проверяется на
каждом такте; после отказа записи тот же уровень повторяется через 15 секунд.
Остановка последнего авто-сиденья снимает таймер модели. Опрос датчиков и CSV
сохраняют собственный 30-секундный такт `CalibrationSampler`.
