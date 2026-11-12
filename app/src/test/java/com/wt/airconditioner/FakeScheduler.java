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
    private long nowMs;

    private final class Task implements Cancellation {
        final long dueAtMs;
        final Runnable action;

        Task(long dueAtMs, Runnable action) {
            this.dueAtMs = dueAtMs;
            this.action = action;
        }

        @Override
        public void cancel() {
            pending.remove(this);
        }
    }

    @Override
    public Cancellation scheduleMillis(long delayMs, Runnable task) {
        Task scheduled = new Task(nowMs + delayMs, task);
        pending.add(scheduled);
        return scheduled;
    }

    /**
     * Прокручивает время, выполняя задачи в порядке срабатывания. Задачи,
     * поставленные по ходу дела, тоже успевают сработать, если попадают в
     * интервал, — иначе каскад 3→2→1→0 не прошёл бы за один вызов.
     */
    void elapse(int minutes) {
        elapseMillis(minutes * 60_000L);
    }

    long nowMs() {
        return nowMs;
    }

    void elapseMillis(long millis) {
        long target = nowMs + millis;
        while (true) {
            Task next = null;
            for (Task task : pending) {
                if (task.dueAtMs <= target
                        && (next == null || task.dueAtMs < next.dueAtMs)) {
                    next = task;
                }
            }
            if (next == null) {
                break;
            }
            pending.remove(next);
            nowMs = next.dueAtMs;
            next.action.run();
        }
        nowMs = target;
    }

    /** Сколько таймеров ждёт срабатывания: 0 в конце каскада — часть проверки. */
    int pendingCount() {
        return pending.size();
    }
}
