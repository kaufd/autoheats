package com.wt.airconditioner;

import com.wt.airconditioner.TemperatureConstants.HeatSequence;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Каскад подогрева 3 → 2 → 1 → 0. Порт lib/src/services/auto_heat_service.dart.
 *
 * Работает от двух вещей: температуры салона и времени. Уровень снижается,
 * когда салон прогрелся до порога — или когда истёк максимальный срок уровня,
 * если прогрева так и не случилось. Второе и есть страховка: без неё холодным
 * утром подогрев остался бы на тройке навсегда.
 *
 * Ни одной ссылки на Android: расписание в минутах проверяется тестами на JVM,
 * а всё, что связано с железом, живёт снаружи — уровни уходят в callback,
 * время идёт через Scheduler.
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
     * Через сколько повторять уровень, который автомобиль не принял. Минута —
     * наименьший шаг Scheduler и разумная пауза: отказ обычно значит, что HVAC
     * занят, а не что команда неверна.
     */
    private static final int RETRY_MINUTES = 1;

    private final Scheduler scheduler;
    private final LogSink logSink;

    private Double currentTemperature;

    private final Map<Seat, LevelCallback> callbacks = new EnumMap<>(Seat.class);
    private final Map<Seat, Scheduler.Cancellation> timers = new EnumMap<>(Seat.class);
    private final Map<Seat, PresetSettings> presets = new EnumMap<>(Seat.class);

    /** Текущий уровень; отсутствие ключа означает «каскад не идёт». */
    private final Map<Seat, Integer> activeLevels = new EnumMap<>(Seat.class);

    /**
     * Кому уже отправили выключение в тёплом салоне. Без этого каждое событие
     * датчика в диапазоне «греть не нужно» слало бы ещё один ноль.
     */
    private final Set<Seat> offSent = EnumSet.noneOf(Seat.class);

    /**
     * Расписание, на котором каскад дошёл до нуля. Повторное событие с той же
     * температурой не должно начинать всё сначала — только смена расписания.
     */
    private final Map<Seat, String> finishedPlans = new EnumMap<>(Seat.class);

    AutoHeatEngine(Scheduler scheduler, LogSink logSink) {
        this.scheduler = scheduler;
        this.logSink = logSink;
    }

    /** Новая температура салона: и от датчика, и от ручного ввода в отладке. */
    void setTemperature(double celsius) {
        currentTemperature = celsius;
        // Копия ключей: обработка одного сиденья не должна ломать обход, если
        // каскад по ходу дела остановится.
        for (Seat seat : new ArrayList<>(callbacks.keySet())) {
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
        callbacks.put(seat, callback);
        if (settings == null) {
            presets.remove(seat);
        } else {
            presets.put(seat, settings);
        }
        activeLevels.remove(seat);
        offSent.remove(seat);
        finishedPlans.remove(seat);
        update(seat);
    }

    void stop(Seat seat) {
        cancelTimer(seat);
        callbacks.remove(seat);
        presets.remove(seat);
        activeLevels.remove(seat);
        offSent.remove(seat);
        finishedPlans.remove(seat);
        log("каскад остановлен: " + seat.title);
    }

    void stopAll() {
        for (Seat seat : new ArrayList<>(callbacks.keySet())) {
            stop(seat);
        }
        currentTemperature = null;
    }

    /** Решение о уровне для одного сиденья. Здесь весь смысл класса. */
    private void update(Seat seat) {
        if (currentTemperature == null) {
            return;
        }
        LevelCallback callback = callbacks.get(seat);
        if (callback == null) {
            return;
        }

        // Запущенный пресет идёт по своим таймерам до конца: человек выбрал
        // расписание сам, и температура его не прерывает.
        if (presets.containsKey(seat) && activeLevels.get(seat) != null) {
            return;
        }

        HeatSequence sequence = sequenceFor(seat);

        // Салон теплее порога — греть нечего.
        if (sequence == null) {
            cancelTimer(seat);
            activeLevels.remove(seat);
            if (offSent.add(seat)) {
                if (!callback.onLevel(0)) {
                    offSent.remove(seat);
                    // Ждать следующего события датчика нельзя: в тёплом
                    // неподвижном салоне его может не быть часами, а сиденье
                    // осталось включённым.
                    scheduleRetry(seat, 0);
                }
            }
            return;
        }

        Integer activeLevel = activeLevels.get(seat);

        // Первый запуск или возвращение из тёплого диапазона.
        if (activeLevel == null) {
            offSent.remove(seat);
            finishedPlans.remove(seat);
            stepDown(seat, 3, sequence, callback);
            return;
        }

        // Каскад отработал. Заново — только если сменилось расписание, иначе
        // каждое событие датчика включало бы тройку по кругу.
        if (activeLevel == 0) {
            if (!sequence.planKey().equals(finishedPlans.get(seat))) {
                finishedPlans.remove(seat);
                stepDown(seat, 3, sequence, callback);
            }
            return;
        }

        // Салон прогрелся сильнее, чем ожидалось: шагаем сразу до уровня,
        // который соответствует нынешней температуре, а не по одному.
        int temperatureLevel = temperatureBasedLevel(currentTemperature, sequence);
        if (temperatureLevel < activeLevel) {
            stepDown(seat, temperatureLevel, sequence, callback);
            return;
        }

        if (currentTemperature >= sequence.stepDownCelsiusFor(activeLevel)) {
            stepDown(seat, activeLevel - 1, sequence, callback);
        }
        // Иначе остаёмся на уровне: сработает таймер, если прогрев не случится.
    }

    private void stepDown(Seat seat, int requestedLevel, HeatSequence sequence,
            LevelCallback callback) {
        // Уровень нулевой длительности пропускается — ровно это и значит пустое
        // поле в редакторе пресетов («сразу с двойки», см. Preset.fromInput).
        // Иначе каскад на нём застревает навсегда: таймер на ноль минут не
        // заводится, а температурные события запущенный пресет не двигают.
        int skipped = requestedLevel;
        while (skipped > 0 && sequence.minutesFor(skipped) <= 0) {
            skipped--;
        }
        final int newLevel = skipped;
        log(String.format(Locale.US, "%s: уровень %d (в салоне %.1f °C)",
                seat.title, newLevel, currentTemperature));
        if (!callback.onLevel(newLevel)) {
            // Автомобиль не подтвердил запись. Уровень в activeLevels не
            // трогаем: забыть его значило бы уйти в ветку «каскад не идёт» и
            // включить тройку заново — при неудавшемся выключении подогрев не
            // гаснет, а разгорается.
            scheduleRetry(seat, newLevel);
            return;
        }
        activeLevels.put(seat, newLevel);

        if (newLevel <= 0) {
            finishedPlans.put(seat, sequence.planKey());
            cancelTimer(seat);
            return;
        }
        scheduleMaxTimer(seat, newLevel - 1, sequence.minutesFor(newLevel), sequence);
    }

    /**
     * Страховка на случай, если салон не прогреется до порога: по истечении
     * максимального срока уровень снижается сам.
     */
    private void scheduleMaxTimer(Seat seat, int nextLevel, int maxMinutes,
            HeatSequence sequence) {
        cancelTimer(seat);
        if (maxMinutes <= 0) {
            return;
        }
        timers.put(seat, scheduler.schedule(maxMinutes, () -> onMaxTimer(seat, nextLevel)));
    }

    private void onMaxTimer(Seat seat, int nextLevel) {
        if (!callbacks.containsKey(seat)) {
            return;
        }
        log(seat.title + ": время уровня вышло, снижаю до " + nextLevel);
        applyLevel(seat, nextLevel);
    }

    /**
     * Установка уровня по таймеру — и по сроку уровня, и по повтору отклонённой
     * записи. Расписание берётся заново: пока шёл таймер, температура могла
     * увести салон в другой диапазон.
     */
    private void applyLevel(Seat seat, int level) {
        LevelCallback callback = callbacks.get(seat);
        if (callback == null) {
            return;
        }

        PresetSettings preset = presets.get(seat);
        if (preset != null) {
            // Запущенный пресет доводится до конца даже если салон успел
            // прогреться выше порога: прерывать его на полпути нельзя.
            stepDown(seat, level, preset.sequence, callback);
            return;
        }

        HeatSequence sequence = sequenceFor(seat);
        if (sequence == null) {
            // Салон прогрелся, пока шёл таймер: расписания больше нет, но
            // выключить сиденье всё равно надо — и повторить, если автомобиль
            // команду не принял. Без повтора именно здесь подогрев остаётся
            // включённым: таймер отработал, а нового расписания нет, и завести
            // следующий переход некому.
            if (callback.onLevel(0)) {
                cancelTimer(seat);
                activeLevels.remove(seat);
                offSent.add(seat);
            } else {
                scheduleRetry(seat, 0);
            }
            return;
        }
        stepDown(seat, level, sequence, callback);
    }

    /**
     * Повтор перехода, который автомобиль не подтвердил. Своим таймером, а не
     * ожиданием события температуры: переход мог быть назначен таймером, и в
     * диапазоне, где нынешний уровень уместен, ни одно событие его не повторит.
     */
    private void scheduleRetry(Seat seat, int level) {
        log(seat.title + ": уровень " + level + " не подтверждён, повтор через "
                + RETRY_MINUTES + " мин");
        cancelTimer(seat);
        timers.put(seat, scheduler.schedule(RETRY_MINUTES, () -> applyLevel(seat, level)));
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

    private HeatSequence sequenceFor(Seat seat) {
        if (currentTemperature == null) {
            return null;
        }
        PresetSettings preset = presets.get(seat);
        if (preset == null) {
            return TemperatureConstants.heatSequenceFor(currentTemperature);
        }
        return currentTemperature >= preset.thresholdCelsius ? null : preset.sequence;
    }

    private void cancelTimer(Seat seat) {
        Scheduler.Cancellation timer = timers.remove(seat);
        if (timer != null) {
            timer.cancel();
        }
    }

    private void log(String message) {
        if (logSink != null) {
            logSink.log(message);
        }
    }
}
