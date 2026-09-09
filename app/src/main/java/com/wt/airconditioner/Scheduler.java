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

    Cancellation schedule(int delayMinutes, Runnable task);
}
