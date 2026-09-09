# Changelog

Все заметные изменения в проекте AutoHeat. Формат следует
[Keep a Changelog](https://keepachangelog.com/ru/1.1.0/),
проект придерживается [Semantic Versioning](https://semver.org/lang/ru/).

## [Unreleased]

## [2.0.0] - 2026-09-09

Полностью нативная реализация. Flutter-приложение версии 1.0.0 удалено, тот же
`applicationId`, но другая кодовая база и другая линейка `versionCode`.

- На вкладку настроек добавлены текущая версия и обновление из GitHub Releases:
  автоматическая проверка при первом открытии вкладки, ручной повтор,
  загрузка APK и запуск системного установщика Android 9.
- Целевая версия Android зафиксирована как Android 9 (API 28).

- Репозиторий окончательно переведён на native Android Gradle-проект. Java,
  Android-ресурсы, тесты и Gradle wrapper подняты из `native-probe/` в корень;
  прежняя Flutter-реализация удалена.
- GitHub Actions теперь проверяет и собирает APK командой
  `./gradlew test lint assembleRelease` с JDK 17 и публикует native APK.

- Исправлена release-подпись: GitHub Actions теперь требует стабильный Android
  keystore из secrets вместо ephemeral debug-keystore runner'а.
- Исправлена установка рядом с оригинальным `com.wt.airconditioner`: AutoHeat v3
  больше не redeclare'ит принадлежащий ему
  `com.wt.airconditioner.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.
- Восстановлен permission envelope оригинального приложения (location/storage/phone,
  overlay, write-settings, расширенные `android.car.*`) и declaration
  `CustomAccessibilityService`, чтобы системные настройки головы выдавали те же
  доступы, что и рабочему AirConditioner.
- Исправлена обработка HVAC-событий на реальном ГУ: native value теперь
  парсится как `String/int/double`, а sentinel raw `-1` больше не показывается
  как `-42.5 °C`; нативные логи плагина выводятся в debug-лог приложения.
- Исправлена граница подключения плагина: native `connect()` теперь ждёт
  готовности `CarHvacManager`, а HVAC read/write ошибки пробрасываются как
  `PlatformException` вместо `raw=-1` или ложного `success`.

## [1.0.0] - 2026-05-25

Первый стабильный релиз.

- Три режима подогрева на каждое сиденье: `manual`, `presets`, `auto`.
- Авто-режим: адаптивный каскад `3 → 2 → 1 → 0` по диапазонам
  температуры салона с max-timer как safety-net. После завершения
  каскад сам не перезапускается, пока температура не уйдёт в другой
  диапазон.
- Пресеты: пользовательские конфигурации с порогом включения и
  фиксированной длительностью уровней. После старта подскок
  температуры не прерывает каскад.
- Сохранение состояния между запусками и автоматическое
  восстановление режимов.
- Foreground-service для работы при свёрнутом приложении.
- Автоматическое выключение сидений по ignition OFF.
- Темы интерфейса: светлая, тёмная, базовая.
- Debug-режим (длительный тап по индикатору температуры салона):
  вкладка «Логи» с живым in-memory буфером и инжектор температуры
  для проверки алгоритма без реального датчика.
- Автоматическая публикация APK на GitHub Releases по push'у тега `v*`.
