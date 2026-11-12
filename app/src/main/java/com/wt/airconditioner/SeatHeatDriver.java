package com.wt.airconditioner;

/**
 * Что ведёт подогрев сидений: принимает температуру салона и выдаёт уровни,
 * пока его не остановят. Один экземпляр на оба сиденья — сиденье приходит
 * параметром, потому что температура салона у них общая.
 *
 * {@link ModelHeatDriver} ведёт авто по модели, а пользовательские
 * расписания передаёт {@link AutoHeatEngine}.
 *
 * Ни одной ссылки на Android: время идёт через {@link Scheduler}.
 */
interface SeatHeatDriver {

    /** Куда уходит решение о новом уровне подогрева. */
    interface LevelCallback {
        /** false — внешний исполнитель не подтвердил запись уровня. */
        boolean onLevel(int level);
    }

    /** Новая температура салона: и от датчика, и от ручного ввода в отладке. */
    void setTemperature(double celsius);

    /** Вести сиденье по своему усмотрению. */
    void start(Seat seat, LevelCallback callback);

    /**
     * Вести сиденье по расписанию, выбранному человеком. Реализация, которая
     * расписаний не понимает, вправе вести его как обычно.
     */
    void start(Seat seat, LevelCallback callback, PresetSettings settings);

    void stop(Seat seat);

    void stopAll();
}
