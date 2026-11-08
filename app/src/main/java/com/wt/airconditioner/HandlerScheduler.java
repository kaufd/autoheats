package com.wt.airconditioner;

import android.os.Handler;
import android.os.Looper;

/**
 * Боевой Scheduler: таймеры каскада на главном потоке процесса.
 *
 * Handler, а не AlarmManager: сон головы каскад всё равно не переживает — при
 * пропадании ACC подогрев обесточивается вместе с ГУ, — а точные будильники
 * требовали бы SCHEDULE_EXACT_ALARM и просыпания процесса ради нечего.
 */
final class HandlerScheduler implements Scheduler {

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public Cancellation scheduleMillis(long delayMs, Runnable task) {
        handler.postDelayed(task, delayMs);
        return () -> handler.removeCallbacks(task);
    }
}
