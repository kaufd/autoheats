package com.wt.airconditioner;

/** Сиденье с подогревом. Порт UserType из lib/src/app_enums.dart. */
enum Seat {
    DRIVER("водитель", "Водитель"),
    PASSENGER("пассажир", "Пассажир");

    /** Для логов: «выключаю подогрев, водитель». */
    final String title;
    /** Для экрана: подпись кнопки. */
    final String label;

    Seat(String title, String label) {
        this.title = title;
        this.label = label;
    }
}
