package com.wt.airconditioner;

/**
 * Отложенный запуск для каскада подогрева. Существует только затем, чтобы в
 * тестах можно было прокрутить полчаса за миллисекунду: иначе расписание в
 * десятки минут было бы непроверяемым.
 */
interface Scheduler {

    /** Отменяемая задача; повторная отмена безвредна. */
    interface Cancellation {
        void cancel();
    }

    default Cancellation schedule(int delayMinutes, Runnable task) {
        return scheduleMillis(delayMinutes * 60_000L, task);
    }

    /** Такт модели короче минуты; часы пресетов используют тот же механизм. */
    Cancellation scheduleMillis(long delayMs, Runnable task);
}
