# AutoHeat: контекст проекта

AutoHeat — native Android-приложение на Java для Android Automotive OS Changan. Единственный актуальный проект Gradle находится в корне репозитория.

## Технологии и структура

- Android Gradle Plugin 8.5.2, Gradle 8.9, JDK 17;
- Java 8 source/target compatibility, `compileSdk`/`targetSdk` 33, `minSdk` 28;
- `android.car` подключён через `app/libs/android.car.jar`;
- приложение и тесты: `app/src/main/` и `app/src/test/`;
- package/application ID: `com.wt.airconditioner`.

Ключевые классы: `MainActivity` отображает UI, `SeatHeatService` владеет foreground-работой и соединением с машиной, `CarHvacProbe` инкапсулирует `android.car.*`, `AutoHeatEngine` содержит алгоритм нагрева, `IgnitionSession` обрабатывает выключение по зажиганию, `PresetStore` сохраняет настройки.

## Рабочие команды

```bash
./gradlew test lint assembleRelease
./gradlew :app:testDebugUnitTest
./gradlew lintRelease
```

Release APK создаётся в `app/build/outputs/apk/release/`. Для подписанного release заданы четыре `AUTOHEAT_*` переменные окружения, перечисленные в README; в CI они заполняются из `ANDROID_KEYSTORE_*` secrets.

## Инварианты

- Не меняйте `applicationId`: он связан с whitelist CarService на голове.
- Не сокращайте manifest permissions без smoke-теста на физической голове.
- `IGNITION_STATE_START` не означает выключенное зажигание; события ACC/OFF выключают сиденья только после наблюдавшегося ON в текущей сессии.
- `raw < 0` у датчика — sentinel «нет данных», а не температура.
- Любое изменение взаимодействия с `android.car.*` требует проверки на физической голове: эмулятор не предоставляет настоящий CarService.

История миграции сохранена в `NATIVE_MIGRATION.md`; она не является инструкцией для сборки или разработки.
