# AutoHeat

Native Android-приложение для автоматического управления подогревом сидений на головном устройстве Changan с Android Automotive OS. Оно читает температуру салона через `android.car.*`, управляет обоими сиденьями, хранит пресеты и работает как foreground-service после закрытия экрана.

## Сборка и проверка

Нужны JDK 17 и Android SDK 33. Из корня репозитория:

```bash
./gradlew test lint assembleRelease
```

Готовый APK: `app/build/outputs/apk/release/AutoHeat-native-v0.6.0-vc260.apk`. Локальная release-сборка без переменных подписи использует debug keystore. Для стабильной подписи задайте `AUTOHEAT_KEYSTORE_PATH`, `AUTOHEAT_KEYSTORE_PASSWORD`, `AUTOHEAT_KEY_ALIAS` и `AUTOHEAT_KEY_PASSWORD`.

## Установка на голове

`applicationId` — `com.wt.airconditioner`: это whitelisted-пакет головы, без которого `CarService` не отдаёт `CarHvacManager`. APK устанавливается вместо прежнего приложения с тем же идентификатором и должен быть подписан тем же ключом для обновления поверх него.

Проверки на устройстве и известные ограничения описаны в [docs/native-validation.md](docs/native-validation.md). Архитектура — в [ARCHITECTURE.md](ARCHITECTURE.md), история перехода — в [NATIVE_MIGRATION.md](NATIVE_MIGRATION.md).

## Релизы

Push тега `v*` запускает GitHub Actions: JDK 17, `./gradlew test lint assembleRelease`, подпись из существующих secrets и публикацию APK из `app/build/outputs/apk/release/`. Перед тегом обновите `versionName` и `versionCode` в `app/build.gradle`, а также секцию `## [X.Y.Z]` в `CHANGELOG.md`.
