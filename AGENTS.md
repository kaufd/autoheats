# AutoHeat repository guide

AutoHeat — native Android Gradle-проект на Java. Исходники приложения находятся в `app/src/main/`, unit-тесты — в `app/src/test/`, Gradle wrapper — в корне.

Перед изменением Java или Android-ресурсов сначала прочитайте затрагиваемый класс/ресурс и соответствующий тест. Не меняйте поведение `android.car.*`, package ID `com.wt.airconditioner`, manifest permissions или ignition-логику без явной задачи и проверки на физической голове Changan.

Обычная проверка из корня:

```bash
./gradlew test lint assembleRelease
```

В CI JDK 17, а release-keystore передаётся через `AUTOHEAT_*` переменные, заполненные из GitHub secrets. Не коммитьте keystore, `local.properties`, `.gradle/` или `build/`.

Исторические материалы о прежней реализации находятся в `NATIVE_MIGRATION.md`; не используйте их как инструкции для текущего проекта.
