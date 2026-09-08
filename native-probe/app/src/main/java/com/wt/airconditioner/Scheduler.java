package com.wt.airconditioner;

/**
 * Отложенный запуск для каскада подогрева. Существует только затем, чтобы в
 * тестах можно было прокрутить полчаса за миллисекунду: во Flutter-версии эту
 * роль играл fake_async, и без неё проверка расписания 15/10/8 минут стала бы
 * непроверяемой.
 */
interface Scheduler {

    /** Отменяемая задача; повторная отмена безвредна. */
    interface Cancellation {
        void cancel();
    }

    Cancellation schedule(int delayMinutes, Runnable task);
}
