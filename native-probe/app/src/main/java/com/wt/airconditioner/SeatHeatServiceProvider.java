package com.wt.airconditioner;

/** Доступ UI-контроллеров к единственному экземпляру фонового сервиса. */
interface SeatHeatServiceProvider {
    SeatHeatService get();
}
