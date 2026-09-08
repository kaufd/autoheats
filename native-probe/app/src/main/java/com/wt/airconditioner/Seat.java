package com.wt.airconditioner;

/** Сиденье с подогревом. Порт UserType из lib/src/app_enums.dart. */
enum Seat {
    DRIVER("водитель"),
    PASSENGER("пассажир");

    final String title;

    Seat(String title) {
        this.title = title;
    }
}
