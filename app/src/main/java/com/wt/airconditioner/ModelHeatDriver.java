package com.wt.airconditioner;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;

/**
 * Применяет модель к выбранным авто-сиденьям; пресеты отдаёт движку расписаний.
 * Уровни принадлежат сервису и меняются только после подтверждения автомобиля.
 * Отдельный 15-секундный тик нужен даже без событий температуры и файла CSV.
 */
final class ModelHeatDriver implements SeatHeatDriver {
    static final long TICK_MS = 15_000L;

    private static final class ActiveSeat {
        final LevelCallback callback;
        Integer failedLevel;
        long failedAtMs;

        ActiveSeat(LevelCallback callback) {
            this.callback = callback;
        }
    }

    private final Scheduler scheduler;
    private final ModelShadow model;
    private final AutoHeatEngine presets;
    private final Runnable refresh;
    private final LogSink log;
    private final Map<Seat, ActiveSeat> active = new EnumMap<>(Seat.class);
    private Scheduler.Cancellation timer;
    private boolean applying;

    ModelHeatDriver(Scheduler scheduler, ModelShadow model, Runnable refresh, LogSink log) {
        this.scheduler = scheduler;
        this.model = model;
        this.refresh = refresh;
        this.log = log;
        this.presets = new AutoHeatEngine(scheduler, log);
    }

    @Override
    public void setTemperature(double celsius) {
        presets.setTemperature(celsius);
        refresh.run();
    }

    @Override
    public void start(Seat seat, LevelCallback callback) {
        presets.stop(seat);
        active.put(seat, new ActiveSeat(callback));
        model.requestDecision(seat);
        refresh.run();
        scheduleTick();
    }

    @Override
    public void start(Seat seat, LevelCallback callback, PresetSettings settings) {
        if (settings == null) {
            start(seat, callback);
            return;
        }
        stop(seat);
        presets.start(seat, callback, settings);
    }

    @Override
    public void stop(Seat seat) {
        active.remove(seat);
        presets.stop(seat);
        if (active.isEmpty() && timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    @Override
    public void stopAll() {
        for (Seat seat : Seat.values()) {
            stop(seat);
        }
    }

    private void scheduleTick() {
        if (timer != null || active.isEmpty()) {
            return;
        }
        timer = scheduler.scheduleMillis(TICK_MS, () -> {
            timer = null;
            refresh.run();
            scheduleTick();
        });
    }

    /** Вызывается после обновления модели; выключение по OFF остаётся у сервиса. */
    void apply(long nowMs, Map<Seat, Integer> levels, boolean ignitionOn, Air air) {
        if (applying || !ignitionOn) {
            return;
        }
        applying = true;
        try {
            for (Seat seat : new ArrayList<>(active.keySet())) {
                ActiveSeat state = active.get(seat);
                if (state == null) {
                    continue;
                }
                int target = target(seat, nowMs, air);
                Integer current = levels.get(seat);
                if (current != null && current == target) {
                    state.failedLevel = null;
                    continue;
                }
                // События и строка CSV не должны превращать отказ HVAC в цикл записи.
                if (state.failedLevel != null && state.failedLevel == target
                        && nowMs - state.failedAtMs < TICK_MS) {
                    continue;
                }
                // callback синхронно пишет строку CSV и снова обновляет модель.
                // applying предотвращает повторную команду из этого вложенного вызова.
                if (state.callback.onLevel(target)) {
                    state.failedLevel = null;
                    log.log("авто, " + seat.title + ": модель → уровень " + target);
                } else {
                    state.failedLevel = target;
                    state.failedAtMs = nowMs;
                    log.log("авто, " + seat.title + ": уровень " + target
                            + " не подтверждён, повтор через 15 с");
                }
            }
        } finally {
            applying = false;
        }
    }

    private int target(Seat seat, long nowMs, Air air) {
        Double cabin = air.cabin(nowMs);
        if (cabin == null || !Double.isFinite(cabin)) {
            return 0;
        }
        // В текущем банке окружение общее для всех сценариев.
        double environment = SeatObserver.environment(cabin, air.outside(nowMs));
        if (!Double.isFinite(environment)
                || environment >= TemperatureConstants.OFF_ABOVE_CELSIUS) {
            return 0;
        }
        ModelShadow.Estimate estimate = model.estimate(seat);
        if (estimate == null || estimate.level == null) {
            return 0;
        }
        // Правило комфорта действует и между минутными решениями.
        return Math.min(estimate.level, cabin > 0 ? 2 : 3);
    }
}
