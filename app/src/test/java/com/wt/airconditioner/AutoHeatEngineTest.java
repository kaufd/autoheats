package com.wt.airconditioner;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Каскад подогрева. Порт test/unit/auto_heat_service_test.dart — номера
 * сценариев сохранены, чтобы поведение можно было сверить с оригиналом
 * построчно.
 *
 * Проверяется здесь то, ради чего каскад и писался: сильный старт на холодном
 * сиденье, плавное снижение и гарантированное выключение. Ошибка в любом из
 * трёх незаметна на глаз и обнаружилась бы зимой в машине.
 */
public class AutoHeatEngineTest {

    private FakeScheduler scheduler;
    private AutoHeatEngine engine;
    private List<Integer> driverLevels;
    private List<Integer> passengerLevels;

    @Before
    public void setUp() {
        scheduler = new FakeScheduler();
        engine = new AutoHeatEngine(scheduler, null);
        driverLevels = new ArrayList<>();
        passengerLevels = new ArrayList<>();
    }

    private void startDriver() {
        engine.start(Seat.DRIVER, level -> driverLevels.add(level));
    }

    /** scenario-1: холодный салон (-3 °C) проходит 3→2→1→0 по таймерам 8/5/7. */
    @Test
    public void coldCabinRunsFullCascadeOnTimers() {
        engine.setTemperature(-3.0);
        startDriver();
        assertEquals(Arrays.asList(3), driverLevels);

        scheduler.elapse(8);
        assertEquals(Arrays.asList(3, 2), driverLevels);

        scheduler.elapse(5);
        assertEquals(Arrays.asList(3, 2, 1), driverLevels);

        scheduler.elapse(7);
        assertEquals(Arrays.asList(3, 2, 1, 0), driverLevels);
        assertEquals("после каскада таймеров не остаётся", 0, scheduler.pendingCount());
    }

    /**
     * scenario-18: салон греется быстрее расписания — уровень снижается
     * досрочно, не дожидаясь таймера, в том числе через границу диапазона.
     */
    @Test
    public void warmingCabinStepsDownAheadOfTimers() {
        engine.setTemperature(-3.0);
        startDriver();
        assertEquals(Arrays.asList(3), driverLevels);

        scheduler.elapse(3);
        engine.setTemperature(-1.0); /** выше cold.level3StepDown (-2 °C) */
        assertEquals("прогрелся до порога — сразу на 2", Arrays.asList(3, 2), driverLevels);

        scheduler.elapse(2);
        engine.setTemperature(9.0); /** warm-диапазон, level2StepDown = 8 °C */
        assertEquals("тёплый салон — сразу на 1", Arrays.asList(3, 2, 1), driverLevels);

        scheduler.elapse(10);
        assertEquals(Arrays.asList(3, 2, 1, 0), driverLevels);
        assertEquals(0, scheduler.pendingCount());
    }

    /**
     * Резкий прогрев перепрыгивает уровень: с тройки сразу на единицу, минуя
     * двойку. Отдельный тест, потому что в сценарии-18 оба правила снижения
     * дают один и тот же ответ — проверено мутацией: без перехода «сразу до
     * уровня по температуре» тот тест остаётся зелёным, а этот падает,
     * показывая 2 вместо 1.
     */
    @Test
    public void sharpWarmingSkipsIntermediateLevel() {
        engine.setTemperature(-3.0);
        startDriver();
        assertEquals(Arrays.asList(3), driverLevels);

        /**
         * 8 °C — warm-диапазон, где уровню 3 соответствует порог 6 °C, а
         * температуре 8 °C — уже уровень 1 (порог уровня 2 равен 8 °C).
         */
        engine.setTemperature(8.0);
        assertEquals("салон прогрет — двойка пропускается", Arrays.asList(3, 1), driverLevels);
    }

    /**
     * scenario-15: в тёплом салоне выключение уходит один раз, сколько бы
     * событий датчик ни прислал. Иначе каждое событие било бы по железу
     * командой, которую оно уже выполнило.
     */
    @Test
    public void reportsOffOnlyOnceWhileCabinStaysWarm() {
        engine.setTemperature(-3.0);
        startDriver();
        assertEquals(Arrays.asList(3), driverLevels);

        engine.setTemperature(12.0);
        assertEquals(Arrays.asList(3, 0), driverLevels);

        engine.setTemperature(13.0);
        engine.setTemperature(14.0);
        assertEquals("повторные события ничего не добавляют", Arrays.asList(3, 0), driverLevels);

        scheduler.elapse(30);
        assertEquals(Arrays.asList(3, 0), driverLevels);
        assertEquals(0, scheduler.pendingCount());
    }

    /**
     * scenario-9: start() до первого события датчика не может решить, с какого
     * уровня начинать — без этой проверки холодным утром сработал бы каскад
     * по случайной/дефолтной температуре вместо реальной.
     */
    @Test
    public void startWithoutKnownTemperatureDoesNothing() {
        startDriver();
        scheduler.elapse(60);
        assertEquals("без температуры уровень не выбрать", Collections.emptyList(), driverLevels);
        assertEquals(0, scheduler.pendingCount());
    }

    /** scenario-2: тёплый салон (7 °C) тоже проходит полный каскад — но по своим таймерам 3/2/5. */
    @Test
    public void warmCabinRunsFullCascadeOnTimers() {
        engine.setTemperature(7.0);
        startDriver();
        assertEquals(Arrays.asList(3), driverLevels);

        scheduler.elapse(3);
        assertEquals(Arrays.asList(3, 2), driverLevels);

        scheduler.elapse(2);
        assertEquals(Arrays.asList(3, 2, 1), driverLevels);

        scheduler.elapse(5);
        assertEquals(Arrays.asList(3, 2, 1, 0), driverLevels);
        assertEquals(0, scheduler.pendingCount());
    }

    /** scenario-3: экстремальный мороз (-15 °C) держит уровни дольше всех остальных диапазонов — 15/10/8 минут. */
    @Test
    public void extremeColdCabinRunsFullCascadeOnTimers() {
        engine.setTemperature(-15.0);
        startDriver();
        assertEquals(Arrays.asList(3), driverLevels);

        scheduler.elapse(15);
        assertEquals(Arrays.asList(3, 2), driverLevels);

        scheduler.elapse(10);
        assertEquals(Arrays.asList(3, 2, 1), driverLevels);

        scheduler.elapse(8);
        assertEquals(Arrays.asList(3, 2, 1, 0), driverLevels);
        assertEquals(0, scheduler.pendingCount());
    }

    /**
     * scenario-4: салон резко прогрелся посреди каскада — таймер отменяется,
     * иначе через несколько минут подогрев включился бы снова сам по себе,
     * хотя в машине давно тепло.
     */
    @Test
    public void offTemperatureDuringCascadeCancelsTimer() {
        engine.setTemperature(-3.0);
        startDriver();
        assertEquals(Arrays.asList(3), driverLevels);

        scheduler.elapse(3);
        engine.setTemperature(12.0);
        assertEquals(Arrays.asList(3, 0), driverLevels);

        scheduler.elapse(30);
        assertEquals("таймер отменён — больше событий нет", Arrays.asList(3, 0), driverLevels);
        assertEquals(0, scheduler.pendingCount());
    }

    /** scenario-5: явная остановка глушит расписание — таймер не должен продолжать тикать в фоне после stop(). */
    @Test
    public void stopAutoHeatHaltsSchedule() {
        engine.setTemperature(-3.0);
        startDriver();
        assertEquals(Arrays.asList(3), driverLevels);

        engine.stop(Seat.DRIVER);
        scheduler.elapse(30);
        assertEquals(Arrays.asList(3), driverLevels);
        assertEquals(0, scheduler.pendingCount());
    }

    /**
     * stopAll глушит каскады, но температура — свойство салона, а не каскада.
     * Её забывали заодно с каскадами, и следующий запуск (зажигание ON после
     * пробуждения головы) вставал в «жду температуру» до ближайшего события
     * датчика — в холодном неподвижном салоне это минуты.
     */
    @Test
    public void stopAllKeepsKnownTemperature() {
        engine.setTemperature(-3.0);
        startDriver();
        assertEquals(Arrays.asList(3), driverLevels);

        engine.stopAll();
        scheduler.elapse(30);
        assertEquals("каскад остановлен", Arrays.asList(3), driverLevels);
        assertEquals(0, scheduler.pendingCount());

        startDriver();
        assertEquals("температура помнится — каскад стартует без нового события",
                Arrays.asList(3, 3), driverLevels);
    }

    /**
     * scenario-6: у водителя и пассажира отдельные callback и расписания —
     * остановка одного сиденья не должна погасить подогрев у другого, хотя
     * температура в салоне у них общая.
     */
    @Test
    public void stoppingOneSeatDoesNotAffectOther() {
        engine.setTemperature(-3.0);
        startDriver();
        engine.start(Seat.PASSENGER, level -> passengerLevels.add(level));
        assertEquals(Arrays.asList(3), driverLevels);
        assertEquals(Arrays.asList(3), passengerLevels);

        engine.stop(Seat.DRIVER);
        scheduler.elapse(8);
        assertEquals("водитель остановлен", Arrays.asList(3), driverLevels);
        assertEquals("пассажир продолжает по своему таймеру", Arrays.asList(3, 2), passengerLevels);

        scheduler.elapse(12);
        assertEquals(Arrays.asList(3, 2, 1, 0), passengerLevels);
        assertEquals(0, scheduler.pendingCount());
    }

    /** scenario-7: повторный start() во время каскада начинает заново с уровня 3, а не продолжает с текущего. */
    @Test
    public void restartDuringCascadeResetsToLevelThree() {
        engine.setTemperature(-3.0);
        startDriver();
        assertEquals(Arrays.asList(3), driverLevels);

        scheduler.elapse(3);
        startDriver();
        assertEquals("перезапуск с уровня 3", Arrays.asList(3, 3), driverLevels);

        scheduler.elapse(20);
        assertEquals(Arrays.asList(3, 3, 2, 1, 0), driverLevels);
        assertEquals(0, scheduler.pendingCount());
    }

    /** scenario-8: температура ровно на границе off-порога (10 °C) — сразу выключение, каскад не запускается вовсе. */
    @Test
    public void temperatureAtOffThresholdReportsOffImmediately() {
        engine.setTemperature(10.0);
        startDriver();
        assertEquals(Arrays.asList(0), driverLevels);

        scheduler.elapse(30);
        assertEquals(Arrays.asList(0), driverLevels);
        assertEquals(0, scheduler.pendingCount());
    }

    /**
     * scenario-10: пресет с собственными длительностями идёт строго по своим
     * таймерам 3/2/1 минута — это расписание пользователь настроил руками в
     * редакторе пресетов, и оно не должно подменяться авто-расписанием.
     */
    @Test
    public void customPresetDurationsDriveTimedCascade() {
        PresetSettings settings = new PresetSettings(5.0, 3, 2, 1);
        engine.setTemperature(4.0);
        engine.start(Seat.DRIVER, level -> driverLevels.add(level), settings);

        assertEquals(Arrays.asList(3), driverLevels);
        scheduler.elapse(3);
        assertEquals(Arrays.asList(3, 2), driverLevels);
        scheduler.elapse(2);
        assertEquals(Arrays.asList(3, 2, 1), driverLevels);
        scheduler.elapse(1);
        assertEquals(Arrays.asList(3, 2, 1, 0), driverLevels);
    }

    /**
     * Пустое поле длительности в редакторе — это «пропустить уровень», а не
     * «держать его вечно». Таймер на ноль минут не заводится, а запущенный
     * пресет не двигают события температуры, так что без пропуска каскад
     * застревает на первом же таком уровне до конца поездки.
     */
    @Test
    public void presetSkipsLevelsWithZeroDuration() {
        PresetSettings settings = new PresetSettings(5.0, 0, 5, 3);
        engine.setTemperature(4.0);
        engine.start(Seat.DRIVER, level -> driverLevels.add(level), settings);

        assertEquals("тройка пропущена — стартуем сразу с двойки",
                Arrays.asList(2), driverLevels);
        scheduler.elapse(5);
        assertEquals(Arrays.asList(2, 1), driverLevels);
        scheduler.elapse(3);
        assertEquals(Arrays.asList(2, 1, 0), driverLevels);
        assertEquals(0, scheduler.pendingCount());
    }

    /** Нулевой последний уровень — каскад заканчивается выключением, а не им. */
    @Test
    public void presetWithZeroLastLevelEndsWithOff() {
        PresetSettings settings = new PresetSettings(5.0, 4, 0, 0);
        engine.setTemperature(4.0);
        engine.start(Seat.DRIVER, level -> driverLevels.add(level), settings);

        assertEquals(Arrays.asList(3), driverLevels);
        scheduler.elapse(4);
        assertEquals("оба нулевых уровня пропущены", Arrays.asList(3, 0), driverLevels);
        assertEquals(0, scheduler.pendingCount());
    }

    /**
     * Салон прогрелся, греть больше нечего, но выключение не прошло. Ждать
     * следующего события датчика нельзя: в тёплом неподвижном салоне его может
     * не быть часами, а сиденье всё это время включено.
     */
    @Test
    public void rejectedOffInWarmCabinIsRetriedByTimer() {
        List<Integer> attempts = new ArrayList<>();
        boolean[] rejectNextOff = {true};
        engine.setTemperature(-3.0);
        engine.start(Seat.DRIVER, level -> {
            attempts.add(level);
            if (level == 0 && rejectNextOff[0]) {
                rejectNextOff[0] = false;
                return false;
            }
            return true;
        });

        engine.setTemperature(20.0); /** выше OFF_ABOVE_CELSIUS — греть нечего */
        assertEquals(Arrays.asList(3, 0), attempts);

        scheduler.elapse(1);
        assertEquals(Arrays.asList(3, 0, 0), attempts);
    }

    /**
     * scenario-11: температура уже на пороге пресета или выше — пресет не
     * стартует вовсе, каким бы ни было расписание внутри него: греть тёплый
     * салон не нужно, даже если пользователь так настроил длительности.
     */
    @Test
    public void customPresetAtOrAboveThresholdStaysOff() {
        PresetSettings settings = new PresetSettings(5.0, 3, 2, 1);
        engine.setTemperature(5.0);
        engine.start(Seat.DRIVER, level -> driverLevels.add(level), settings);

        assertEquals(Arrays.asList(0), driverLevels);
        scheduler.elapse(10);
        assertEquals(Arrays.asList(0), driverLevels);
    }

    /**
     * scenario-13: небольшое потепление внутри диапазона не должно сдёргивать
     * уровень раньше времени — иначе подогрев мигал бы на каждое мелкое
     * колебание датчика вместо того, чтобы держать уровень до порога.
     */
    @Test
    public void gentleWarmingWithinRangeKeepsCurrentLevel() {
        engine.setTemperature(-3.0);
        startDriver();
        assertEquals(Arrays.asList(3), driverLevels);

        scheduler.elapse(3);
        engine.setTemperature(-2.5); /** всё ещё ниже cold.level3StepDown (-2 °C) */
        assertEquals("порог не пройден — уровень не меняется", Arrays.asList(3), driverLevels);

        scheduler.elapse(5);
        assertEquals("max-timer 8 мин истёк", Arrays.asList(3, 2), driverLevels);
        scheduler.elapse(5);
        assertEquals(Arrays.asList(3, 2, 1), driverLevels);
        scheduler.elapse(7);
        assertEquals(Arrays.asList(3, 2, 1, 0), driverLevels);
        assertEquals(0, scheduler.pendingCount());
    }

    /**
     * scenario-14: потепление через границу диапазона (cold → cool) снижает
     * уровень на один шаг от текущего, а не перезапускает каскад с тройки —
     * иначе смена диапазона выглядела бы как ложный холодный старт и грела
     * бы сиденье сильнее, чем нужно уже прогретому салону.
     */
    @Test
    public void warmingAcrossRangeStepsDownWithoutRestart() {
        engine.setTemperature(-3.0);
        startDriver();
        assertEquals(Arrays.asList(3), driverLevels);

        scheduler.elapse(3);
        engine.setTemperature(1.0); /** cool-диапазон, но ниже level3StepDown (2 °C) */
        assertEquals("порог cool ещё не пройден — без step-down", Arrays.asList(3), driverLevels);

        engine.setTemperature(3.0); /** выше level3StepDown (2 °C) */
        assertEquals("step-down 3→2, НЕ перезапуск с тройки", Arrays.asList(3, 2), driverLevels);

        scheduler.elapse(12);
        assertEquals(Arrays.asList(3, 2, 1, 0), driverLevels);
        assertEquals(0, scheduler.pendingCount());
    }

    /**
     * scenario-16: явный повторный start() посреди каскада — то же правило
     * сброса, что и в scenario-7, здесь дополнительно проверен ещё один шаг
     * таймера после перезапуска.
     */
    @Test
    public void explicitRestartDuringCascadeResetsToLevelThree() {
        engine.setTemperature(-3.0);
        startDriver();
        assertEquals(Arrays.asList(3), driverLevels);

        scheduler.elapse(3);
        startDriver();
        assertEquals(Arrays.asList(3, 3), driverLevels);

        scheduler.elapse(8);
        assertEquals(Arrays.asList(3, 3, 2), driverLevels);
    }

    /**
     * Отработавший каскад не начинается заново от того, что датчик прислал ту
     * же температуру ещё раз. Во Flutter-версии это чинили отдельным фиксом:
     * без защиты подогрев уходил в бесконечный круг 3→2→1→0→3 и грел до
     * посинения. Ни один из перенесённых сценариев этого не проверял —
     * подтверждено мутацией.
     */
    @Test
    public void finishedCascadeDoesNotRestartOnSameTemperature() {
        engine.setTemperature(-3.0);
        startDriver();
        scheduler.elapse(20);
        assertEquals(Arrays.asList(3, 2, 1, 0), driverLevels);

        engine.setTemperature(-3.0);
        assertEquals("та же температура — каскад не начинается заново",
                Arrays.asList(3, 2, 1, 0), driverLevels);
        assertEquals(0, scheduler.pendingCount());
    }

    /**
     * А вот похолодание после отработавшего каскада — законный повод начать
     * сначала: расписание сменилось, значит это уже другая ситуация. Обратная
     * половина того же правила, и без неё подогрев не включился бы на морозе
     * после того, как один раз отработал.
     */
    @Test
    public void finishedCascadeRestartsWhenScheduleChanges() {
        engine.setTemperature(-3.0);
        startDriver();
        scheduler.elapse(20);
        assertEquals(Arrays.asList(3, 2, 1, 0), driverLevels);

        engine.setTemperature(-15.0); /** extreme вместо cold — другое расписание */
        assertEquals("сменилось расписание — снова тройка",
                Arrays.asList(3, 2, 1, 0, 3), driverLevels);
    }

    /**
     * Запущенный пресет доводится до конца, даже если салон успел прогреться
     * выше его порога. Порог решает только, стартовать ли: человек выбрал
     * расписание сам, и обрывать его на полпути — не то, о чём он просил.
     */
    @Test
    public void runningPresetIgnoresWarmingCabin() {
        PresetSettings settings = new PresetSettings(5.0, 3, 2, 1);
        engine.setTemperature(4.0);
        engine.start(Seat.DRIVER, level -> driverLevels.add(level), settings);
        assertEquals(Arrays.asList(3), driverLevels);

        engine.setTemperature(20.0); /** намного выше порога пресета */
        assertEquals("пресет не прерывается", Arrays.asList(3), driverLevels);

        scheduler.elapse(3);
        assertEquals("и продолжает идти по своим таймерам", Arrays.asList(3, 2), driverLevels);
    }

    /**
     * Порт dart-теста «initialize(hvac): emitTemperature через cabin listener
     * запускает расписание». В движке нет отдельного listener поверх HVAC —
     * setTemperature и есть точка входа для любого источника температуры,
     * поэтому сценарий сводится к комбинации scenario-8 (шумовое off-событие
     * до регистрации callback отбрасывается) и scenario-1 (дальше обычный
     * холодный каскад).
     */
    @Test
    public void offEventBeforeStartIsDiscardedThenColdCascadeRuns() {
        engine.setTemperature(50.0); /** известное off-состояние, callback ещё не зарегистрирован */
        startDriver();
        driverLevels.clear(); /** отбросить стартовый шум (callback(0) на temp=50) */

        engine.setTemperature(-3.0); /** реальное событие датчика */
        assertEquals(Arrays.asList(3), driverLevels);

        scheduler.elapse(20);
        assertEquals(Arrays.asList(3, 2, 1, 0), driverLevels);
        assertEquals(0, scheduler.pendingCount());
    }

    /**
     * Отклонённая запись повторяется тем же уровнем. Проверяется закрывающий
     * ноль: сиденье при отказе физически остаётся горячим, а событий
     * температуры, способных повторить переход, в этом диапазоне не будет —
     * уровень 1 при −3 °C как раз соответствует расписанию.
     */
    @Test
    public void rejectedLevelIsRetriedInsteadOfRestartingCascade() {
        List<Integer> attempts = new ArrayList<>();
        boolean[] rejectNextOff = {true};
        engine.setTemperature(-3.0);
        engine.start(Seat.DRIVER, level -> {
            attempts.add(level);
            if (level == 0 && rejectNextOff[0]) {
                rejectNextOff[0] = false;
                return false;
            }
            return true;
        });

        scheduler.elapse(8 + 5 + 7);
        assertEquals("каскад дошёл до нуля, но его не приняли",
                Arrays.asList(3, 2, 1, 0), attempts);

        scheduler.elapse(1);
        assertEquals("повторяется тот же ноль, а не новая тройка",
                Arrays.asList(3, 2, 1, 0, 0), attempts);
        assertEquals("после принятого нуля таймеров не остаётся",
                0, scheduler.pendingCount());
    }

    /**
     * Событие температуры между отказом и повтором не должно поднимать уровень:
     * тройка после неудавшегося выключения — худший из возможных исходов.
     */
    @Test
    public void temperatureEventAfterRejectedLevelDoesNotEscalate() {
        List<Integer> attempts = new ArrayList<>();
        engine.setTemperature(-3.0);
        engine.start(Seat.DRIVER, level -> {
            attempts.add(level);
            return level != 0;
        });

        scheduler.elapse(8 + 5 + 7);
        assertEquals(Arrays.asList(3, 2, 1, 0), attempts);

        engine.setTemperature(-3.0);
        assertEquals("уровень не меняется — переход всё ещё ждёт повтора",
                Arrays.asList(3, 2, 1, 0), attempts);
    }
}
