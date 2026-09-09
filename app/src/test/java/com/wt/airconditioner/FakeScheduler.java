package com.wt.airconditioner;

import java.util.ArrayList;
import java.util.List;

/**
 * Часы для тестов: позволяют прокрутить полчаса каскада мгновенно. Замена
 * fake_async из Flutter-версии — без неё расписание 15/10/8 минут пришлось бы
 * проверять на голове вслепую.
 */
final class FakeScheduler implements Scheduler {

    private final List<Task> pending = new ArrayList<>();
    private long nowMinutes;

    private final class Task implements Cancellation {
        final long dueAtMinutes;
        final Runnable action;

        Task(long dueAtMinutes, Runnable action) {
            this.dueAtMinutes = dueAtMinutes;
            this.action = action;
        }

        @Override
        public void cancel() {
            pending.remove(this);
        }
    }

    @Override
    public Cancellation schedule(int delayMinutes, Runnable task) {
        Task scheduled = new Task(nowMinutes + delayMinutes, task);
        pending.add(scheduled);
        return scheduled;
    }

    /**
     * Прокручивает время, выполняя задачи в порядке срабатывания. Задачи,
     * поставленные по ходу дела, тоже успевают сработать, если попадают в
     * интервал, — иначе каскад 3→2→1→0 не прошёл бы за один вызов.
     */
    void elapse(int minutes) {
        long target = nowMinutes + minutes;
        while (true) {
            Task next = null;
            for (Task task : pending) {
                if (task.dueAtMinutes <= target
                        && (next == null || task.dueAtMinutes < next.dueAtMinutes)) {
                    next = task;
                }
            }
            if (next == null) {
                break;
            }
            pending.remove(next);
            nowMinutes = next.dueAtMinutes;
            next.action.run();
        }
        nowMinutes = target;
    }

    /** Сколько таймеров ждёт срабатывания: 0 в конце каскада — часть проверки. */
    int pendingCount() {
        return pending.size();
    }
}
