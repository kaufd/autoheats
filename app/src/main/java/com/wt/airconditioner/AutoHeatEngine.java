package com.wt.airconditioner;

import com.wt.airconditioner.TemperatureConstants.HeatSequence;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Каскад подогрева 3 → 2 → 1 → 0 от температуры салона и времени. Уровень
 * снижается, когда салон прогрелся до порога, — или когда истёк максимальный
 * срок уровня: без этой страховки холодным утром подогрев остался бы на тройке
 * навсегда.
 *
 * Ни одной ссылки на Android: уровни уходят в callback, время идёт через
 * Scheduler, а расписание в минутах проверяется тестами на JVM.
 */
final class AutoHeatEngine {

    /** Куда уходит решение о новом уровне подогрева. */
    interface LevelCallback {
        /** false — внешний исполнитель не подтвердил запись уровня. */
        boolean onLevel(int level);
    }

    /** Диагностика на экран; в тестах обычно не нужна. */
    interface LogSink {
        void log(String message);
    }

    /**
     * Минута — наименьший шаг Scheduler и разумная пауза: отказ обычно значит,
     * что HVAC занят, а не что команда неверна.
     */
    private static final int RETRY_MINUTES = 1;

    /**
     * Всё, что движок помнит про одно сиденье.
     *
     * level и offSent намеренно разные поля, хотя оба говорят про ноль:
     * «level == null при offSent» — «в тёплом салоне выключено, но каскад не
     * начинался», а «level == 0» — «каскад дошёл до конца». Разницу читает гейт
     * запущенного пресета в update(): сведи их в одно поле — и пресет,
     * применённый в тёплом салоне, уже не стартует, когда салон остынет.
     */
    private static final class SeatState {
        final LevelCallback callback;
        /** null — авто-режим: расписание выбирается по температуре салона. */
        final PresetSettings preset;

        Scheduler.Cancellation timer;
        /** Текущий уровень; null — каскад не идёт. */
        Integer level;
        /** Ноль уже отправлен в диапазоне «греть не нужно». */
        boolean offSent;
        /** Расписание, на котором каскад дошёл до нуля; null — не доходил. */
        String finishedPlan;

        SeatState(LevelCallback callback, PresetSettings preset) {
            this.callback = callback;
            this.preset = preset;
        }
    }

    private final Scheduler scheduler;
    private final LogSink logSink;

    private Double currentTemperature;

    /** Отсутствие ключа означает «сиденьем движок не управляет». */
    private final Map<Seat, SeatState> states = new EnumMap<>(Seat.class);

    AutoHeatEngine(Scheduler scheduler, LogSink logSink) {
        this.scheduler = scheduler;
        this.logSink = logSink;
    }

    /** Новая температура салона: и от датчика, и от ручного ввода в отладке. */
    void setTemperature(double celsius) {
        currentTemperature = celsius;
        /**
         * Копия ключей: остановка каскада по ходу дела не должна ломать обход.
         */
        for (Seat seat : new ArrayList<>(states.keySet())) {
            update(seat);
        }
    }

    /** Автоматический режим: расписание выбирается по температуре салона. */
    void start(Seat seat, LevelCallback callback) {
        start(seat, callback, null);
    }

    /**
     * Пресет: расписание задано человеком, температура решает только, стартовать
     * ли вообще.
     */
    void start(Seat seat, LevelCallback callback, PresetSettings settings) {
        /**
         * Состояние сиденья заменяется целиком: не сняв таймер прошлого каскада,
         * мы потеряли бы ссылку на него, и он сработал бы поверх нового.
         */
        cancelTimer(states.get(seat));
        states.put(seat, new SeatState(callback, settings));
        update(seat);
    }

    void stop(Seat seat) {
        cancelTimer(states.remove(seat));
        log("каскад остановлен: " + seat.title);
    }

    /**
     * Температуру не забываем: она свойство салона, а не состояние каскада.
     * Иначе следующий запуск упёрся бы в «жду температуру» до ближайшего события
     * датчика — в холодном неподвижном салоне это минуты.
     */
    void stopAll() {
        for (Seat seat : new ArrayList<>(states.keySet())) {
            stop(seat);
        }
    }

    /** Решение о уровне для одного сиденья. Здесь весь смысл класса. */
    private void update(Seat seat) {
        if (currentTemperature == null) {
            return;
        }
        SeatState state = states.get(seat);
        if (state == null) {
            return;
        }

        /**
         * Запущенный пресет идёт по своим таймерам до конца: человек выбрал
         * расписание сам, и температура его не прерывает.
         */
        if (state.preset != null && state.level != null) {
            return;
        }

        HeatSequence sequence = sequenceFor(state);

        /**
         * Салон теплее порога — греть нечего.
         */
        if (sequence == null) {
            cancelTimer(state);
            state.level = null;
            if (!state.offSent) {
                state.offSent = true;
                if (!state.callback.onLevel(0)) {
                    state.offSent = false;
                    /**
                     * Ждать следующего события датчика нельзя: в тёплом
                     * неподвижном салоне его может не быть часами, а сиденье
                     * осталось включённым.
                     */
                    scheduleRetry(seat, 0);
                }
            }
            return;
        }

        /**
         * Первый запуск или возвращение из тёплого диапазона.
         */
        if (state.level == null) {
            state.offSent = false;
            state.finishedPlan = null;
            stepDown(seat, state, 3, sequence);
            return;
        }

        /**
         * Каскад отработал. Заново — только если сменилось расписание, иначе
         * каждое событие датчика включало бы тройку по кругу.
         */
        if (state.level == 0) {
            if (!sequence.planKey().equals(state.finishedPlan)) {
                state.finishedPlan = null;
                stepDown(seat, state, 3, sequence);
            }
            return;
        }

        /**
         * Салон прогрелся сильнее, чем ожидалось: шагаем сразу до уровня,
         * который соответствует нынешней температуре, а не по одному.
         */
        int temperatureLevel = temperatureBasedLevel(currentTemperature, sequence);
        if (temperatureLevel < state.level) {
            stepDown(seat, state, temperatureLevel, sequence);
            return;
        }

        if (currentTemperature >= sequence.stepDownCelsiusFor(state.level)) {
            stepDown(seat, state, state.level - 1, sequence);
        }
        /**
         * Иначе остаёмся на уровне: сработает таймер, если прогрев не случится.
         */
    }

    private void stepDown(Seat seat, SeatState state, int requestedLevel,
            HeatSequence sequence) {
        /**
         * Уровень нулевой длительности пропускается — это и значит пустое поле в
         * редакторе пресетов («сразу с двойки», см. Preset.fromInput). Иначе
         * каскад застревает: таймер на ноль минут не заводится, а температурные
         * события запущенный пресет не двигают.
         */
        int skipped = requestedLevel;
        while (skipped > 0 && sequence.minutesFor(skipped) <= 0) {
            skipped--;
        }
        final int newLevel = skipped;
        log(String.format(Locale.US, "%s: уровень %d (в салоне %.1f °C)",
                seat.title, newLevel, currentTemperature));
        if (!state.callback.onLevel(newLevel)) {
            /**
             * state.level не трогаем: забыв его, мы ушли бы в ветку «каскад не
             * идёт» и включили тройку заново — при неудавшемся выключении
             * подогрев не гаснет, а разгорается.
             */
            scheduleRetry(seat, newLevel);
            return;
        }
        state.level = newLevel;

        if (newLevel <= 0) {
            state.finishedPlan = sequence.planKey();
            cancelTimer(state);
            return;
        }
        scheduleMaxTimer(seat, state, newLevel - 1, sequence.minutesFor(newLevel));
    }

    /**
     * Страховка на случай, если салон не прогреется до порога: по истечении
     * максимального срока уровень снижается сам.
     */
    private void scheduleMaxTimer(Seat seat, SeatState state, int nextLevel, int maxMinutes) {
        cancelTimer(state);
        if (maxMinutes <= 0) {
            return;
        }
        state.timer = scheduler.schedule(maxMinutes, () -> onMaxTimer(seat, nextLevel));
    }

    private void onMaxTimer(Seat seat, int nextLevel) {
        if (!states.containsKey(seat)) {
            return;
        }
        log(seat.title + ": время уровня вышло, снижаю до " + nextLevel);
        applyLevel(seat, nextLevel);
    }

    /**
     * Уровень по таймеру — и по сроку уровня, и по повтору отклонённой записи.
     * Расписание берётся заново: пока шёл таймер, салон мог уйти в другой
     * диапазон.
     */
    private void applyLevel(Seat seat, int level) {
        SeatState state = states.get(seat);
        if (state == null) {
            return;
        }

        if (state.preset != null) {
            /**
             * Запущенный пресет доводится до конца даже если салон успел
             * прогреться выше порога: прерывать его на полпути нельзя.
             */
            stepDown(seat, state, level, state.preset.sequence);
            return;
        }

        HeatSequence sequence = sequenceFor(state);
        if (sequence == null) {
            /**
             * Салон прогрелся, пока шёл таймер: расписания нет, но выключить
             * сиденье надо — и повторить, если команду не приняли. Иначе таймер
             * отработал, расписания нет, и завести следующий переход некому.
             */
            if (state.callback.onLevel(0)) {
                cancelTimer(state);
                state.level = null;
                state.offSent = true;
            } else {
                scheduleRetry(seat, 0);
            }
            return;
        }
        stepDown(seat, state, level, sequence);
    }

    /**
     * Своим таймером, а не ожиданием события температуры: в диапазоне, где
     * нынешний уровень уместен, ни одно событие переход не повторит.
     */
    private void scheduleRetry(Seat seat, int level) {
        SeatState state = states.get(seat);
        if (state == null) {
            return;
        }
        log(seat.title + ": уровень " + level + " не подтверждён, повтор через "
                + RETRY_MINUTES + " мин");
        cancelTimer(state);
        state.timer = scheduler.schedule(RETRY_MINUTES, () -> applyLevel(seat, level));
    }

    /** Уровень, который соответствует нынешней температуре в этом расписании. */
    private static int temperatureBasedLevel(double celsius, HeatSequence sequence) {
        if (celsius >= sequence.level1StepDownCelsius) {
            return 0;
        }
        if (celsius >= sequence.level2StepDownCelsius) {
            return 1;
        }
        if (celsius >= sequence.level3StepDownCelsius) {
            return 2;
        }
        return 3;
    }

    private HeatSequence sequenceFor(SeatState state) {
        if (currentTemperature == null) {
            return null;
        }
        if (state.preset == null) {
            return TemperatureConstants.heatSequenceFor(currentTemperature);
        }
        return currentTemperature >= state.preset.thresholdCelsius ? null : state.preset.sequence;
    }

    /** Принимает null: вызывается и там, где сиденьем никто не управляет. */
    private static void cancelTimer(SeatState state) {
        if (state != null && state.timer != null) {
            state.timer.cancel();
            state.timer = null;
        }
    }

    private void log(String message) {
        if (logSink != null) {
            logSink.log(message);
        }
    }
}
