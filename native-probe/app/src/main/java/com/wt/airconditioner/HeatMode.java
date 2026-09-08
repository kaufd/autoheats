package com.wt.airconditioner;

/**
 * Режим сиденья. Порт HeatMode из lib/src/app_enums.dart — те же три,
 * с теми же правилами.
 */
enum HeatMode {
    /** Уровень держит человек: что выбрал, то и греет, пока не выключит. */
    MANUAL("Вручную"),
    /** Расписание, сохранённое пользователем; запускается его выбором. */
    PRESETS("Пресеты"),
    /** Каскад по температуре салона, стартует по зажиганию ON. */
    AUTO("Авто");

    final String title;

    HeatMode(String title) {
        this.title = title;
    }
}
