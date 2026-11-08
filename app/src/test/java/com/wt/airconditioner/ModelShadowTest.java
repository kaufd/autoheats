package com.wt.airconditioner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Фоновый расчёт модели: когда создаются наблюдатели, когда считается решение,
 * что пишется в лог и что происходит при сбое. Физика — в SeatObserverTest и
 * SeatThermostatTest; здесь только стык.
 */
public class ModelShadowTest {

    private static final long WALL = 1_757_000_000_000L;

    private static final class MemoryStore implements ModelShadow.Store {
        final Map<Seat, String> snapshots = new EnumMap<>(Seat.class);
        boolean failing;

        @Override
        public String load(Seat seat) {
            return snapshots.get(seat);
        }

        @Override
        public void save(Map<Seat, String> saved) {
            if (failing) {
                throw new IllegalStateException("диск");
            }
            snapshots.putAll(saved);
        }
    }

    private final MemoryStore store = new MemoryStore();
    private final List<String> log = new ArrayList<>();
    private final Map<Seat, Integer> levels = new EnumMap<>(Seat.class);

    private ModelShadow shadow() {
        return new ModelShadow(store, log::add);
    }

    /**
     * Воздух, полученный ровно в этот момент, — то есть заведомо достоверный.
     * Возраст проверяется в AirTest; здесь проверяется стык, и null значит
     * «показания нет», а не «протухло».
     */
    private static Air air(long elapsedMs, Double cabin, Double outside) {
        return new Air(cabin, cabin == null ? null : elapsedMs,
                outside, outside == null ? null : elapsedMs);
    }

    /** Строки решений сиденья; строка восстановления сюда не входит. */
    private List<String> decisions(Seat seat) {
        List<String> found = new ArrayList<>();
        for (String line : containing(log, "модель, " + seat.title + ": ")) {
            if (line.contains("поверхность база")) {
                found.add(line);
            }
        }
        return found;
    }

    private static List<String> containing(List<String> lines, String part) {
        List<String> found = new ArrayList<>();
        for (String line : lines) {
            if (line.contains(part)) {
                found.add(line);
            }
        }
        return found;
    }

    @Test
    public void waitsForReliableAirBeforeRestoring() {
        /**
         * Без воздуха не из чего строить ни неизвестную историю, ни оболочку
         * перерыва — и затирать годный снимок нельзя.
         */
        store.snapshots.put(Seat.DRIVER, "годный снимок");
        ModelShadow shadow = shadow();
        shadow.update(0L, WALL, levels, true, air(0L, null, null));
        assertNull(shadow.estimate(Seat.DRIVER));
        assertEquals("годный снимок", store.snapshots.get(Seat.DRIVER));

        shadow.update(30_000L, WALL, levels, true, air(30_000L, 10.0, null));
        assertNotNull(shadow.estimate(Seat.DRIVER));
        assertTrue(containing(log, "отключена").isEmpty());
    }

    @Test
    public void restoresBothSeatsAndSavesSnapshots() {
        ModelShadow shadow = shadow();
        shadow.update(0L, WALL, levels, false, air(0L, 10.0, null));
        assertNotNull(shadow.estimate(Seat.DRIVER));
        assertNotNull(shadow.estimate(Seat.PASSENGER));
        assertNotNull(store.snapshots.get(Seat.DRIVER));
        assertNotNull(store.snapshots.get(Seat.PASSENGER));
        assertEquals(2, containing(log, "неизвестная история").size());

        ModelShadow restarted = shadow();
        restarted.update(0L, WALL, levels, false, air(0L, 10.0, null));
        assertEquals(2, containing(log, "снимок, перерыв").size());
    }

    @Test
    public void levelChangeWhileCabinIsMissingIsNotLost() {
        /**
         * Утро: воздух ещё недостоверен, а каскад уже поставил тройку. Когда
         * воздух вернулся, минута до каскада считается нулём, две после — тройкой.
         */
        store.snapshots.put(Seat.DRIVER,
                SeatObserverTest.snapshot(Seat.DRIVER, "0", 1, 20.0, 20.0, 20.0));
        levels.put(Seat.DRIVER, 0);
        ModelShadow shadow = shadow();
        shadow.update(0L, WALL, levels, true, air(0L, 20.0, null));
        levels.put(Seat.DRIVER, 3);
        shadow.update(60_000L, WALL + 60_000L, levels, true, air(60_000L, null, null));
        shadow.update(180_000L, WALL + 180_000L, levels, true, air(180_000L, 20.0, null));

        SeatThermalModel.Parameters base = new SeatThermalModel.Parameters();
        SeatThermalModel.State idle = SeatThermalModel.advance(
                new SeatThermalModel.State(20, 20), 0, 20, base, 1);
        double expected = SeatThermalModel.advance(idle, 3, 20, base, 2).surface;
        assertEquals(expected, shadow.estimate(Seat.DRIVER).base.low, 1e-9);
        assertEquals(expected, shadow.estimate(Seat.DRIVER).base.high, 1e-9);
    }

    @Test
    public void snapshotKeepsTheTimeOfCountedState() {
        /**
         * Без воздуха состояние не досчитано: снимок с «сейчас» потерял бы
         * этот кусок перерыва при перезапуске.
         */
        store.snapshots.put(Seat.DRIVER,
                SeatObserverTest.snapshot(Seat.DRIVER, "0", 0, 20.0, 20.0, 20.0));
        levels.put(Seat.DRIVER, 0);
        ModelShadow shadow = shadow();
        shadow.update(0L, WALL, levels, false, air(0L, 20.0, null));
        shadow.update(600_000L, WALL + 600_000L, levels, false, air(600_000L, null, null));

        ModelShadow restarted = shadow();
        restarted.update(0L, WALL + 600_000L, levels, false, air(0L, 20.0, null));
        assertEquals(1, containing(log, "водитель: снимок, перерыв 10.0 мин").size());
    }

    @Test
    public void decidesOnlyWhileIgnitionIsOn() {
        ModelShadow shadow = shadow();
        shadow.update(0L, WALL, levels, false, air(0L, -15.0, null));
        assertNull(shadow.estimate(Seat.DRIVER).level);
        assertTrue(decisions(Seat.DRIVER).isEmpty());

        shadow.update(1_000L, WALL, levels, true, air(1_000L, -15.0, null));
        assertNotNull(shadow.estimate(Seat.DRIVER).level);
        assertEquals(1, decisions(Seat.DRIVER).size());

        shadow.update(2_000L, WALL, levels, false, air(2_000L, -15.0, null));
        assertNull(shadow.estimate(Seat.DRIVER).level);
    }

    @Test
    public void regularDecisionWaitsAMinute() {
        /**
         * Промёрзшее сиденье при −25 °C — тройка. Салон прогрелся выше нуля, и
         * тройка больше не разрешена: решение, посчитанное сразу, сменилось бы.
         * Через 30 секунд оно прежнее, через минуту — новое.
         */
        store.snapshots.put(Seat.DRIVER,
                SeatObserverTest.snapshot(Seat.DRIVER, "0", 1, -25.0, -25.0, -25.0));
        levels.put(Seat.DRIVER, 0);
        ModelShadow shadow = shadow();
        shadow.update(0L, WALL, levels, true, air(0L, -25.0, null));
        assertEquals(Integer.valueOf(3), shadow.estimate(Seat.DRIVER).level);

        shadow.update(30_000L, WALL, levels, true, air(30_000L, 5.0, null));
        assertEquals(Integer.valueOf(3), shadow.estimate(Seat.DRIVER).level);

        shadow.update(60_000L, WALL, levels, true, air(60_000L, 5.0, null));
        assertEquals(Integer.valueOf(2), shadow.estimate(Seat.DRIVER).level);
    }

    @Test
    public void lostCabinTurnsDecisionToZeroWithoutWaiting() {
        /** Раздел 2 спецификации: салон недостоверен — ноль, а не прежний уровень. */
        store.snapshots.put(Seat.DRIVER,
                SeatObserverTest.snapshot(Seat.DRIVER, "0", 1, -15.0, -15.0, -15.0));
        levels.put(Seat.DRIVER, 0);
        ModelShadow shadow = shadow();
        shadow.update(0L, WALL, levels, true, air(0L, -15.0, null));
        assertTrue(shadow.estimate(Seat.DRIVER).level > 0);

        shadow.update(30_000L, WALL, levels, true, air(30_000L, null, null));
        assertEquals(Integer.valueOf(0), shadow.estimate(Seat.DRIVER).level);
        List<String> lines = decisions(Seat.DRIVER);
        assertTrue(lines.get(lines.size() - 1).contains("салон недостоверен"));
    }

    @Test
    public void firstKnownLevelDoesNotDelayRaising() {
        /**
         * Уровень ещё не прочитан, затем прочитан ноль. Это первый увиденный
         * уровень, а не смена: трёхминутного запрета повышения нет.
         */
        store.snapshots.put(Seat.DRIVER,
                SeatObserverTest.snapshot(Seat.DRIVER, "0", 1, -15.0, -15.0, -15.0));
        ModelShadow shadow = shadow();
        shadow.update(0L, WALL, levels, true, air(0L, null, null));
        levels.put(Seat.DRIVER, 0);
        shadow.update(1_000L, WALL, levels, true, air(1_000L, -15.0, null));
        assertTrue(shadow.estimate(Seat.DRIVER).level > 0);
    }

    @Test
    public void overheatForecastTurnsDecisionToZeroWithoutWaiting() {
        /**
         * Неизвестная история в тёплом салоне: верхняя граница — горячее
         * кресло. На тройке прогноз перегрева срабатывает на ближайшем
         * обновлении, минуту не ждёт и в лог попадает один раз.
         */
        levels.put(Seat.DRIVER, 3);
        ModelShadow shadow = shadow();
        shadow.update(0L, WALL, levels, true, air(0L, 25.0, null));
        shadow.update(30_000L, WALL, levels, true, air(30_000L, 25.0, null));
        shadow.update(45_000L, WALL, levels, true, air(45_000L, 25.0, null));
        assertEquals(1, containing(decisions(Seat.DRIVER), "перегрев в прогнозе").size());
        assertEquals(Integer.valueOf(0), shadow.estimate(Seat.DRIVER).level);
    }

    @Test
    public void overheatForecastIsNotOverwrittenByRegularDecision() {
        /**
         * Мат остыл, поверхность горячая, тройка: прогноз перегрева требует
         * ноль, а обычный выбор на том же минутном обновлении дал бы единицу.
         * Пока прогноз держится, в ряду и логе — ноль перегрева.
         */
        levels.put(Seat.DRIVER, 3);
        store.snapshots.put(Seat.DRIVER,
                SeatObserverTest.snapshot(Seat.DRIVER, "3", 1, 30.0, 25.0, 37.75));
        ModelShadow shadow = shadow();
        shadow.update(0L, WALL, levels, true, air(0L, 30.0, null));
        assertEquals(Integer.valueOf(0), shadow.estimate(Seat.DRIVER).level);
        for (String line : decisions(Seat.DRIVER)) {
            assertTrue(line, line.contains("перегрев в прогнозе"));
        }
    }

    @Test
    public void newPlanAtTheSameLevelIsNotLogged() {
        /**
         * Промёрзшее сиденье на тройке: через минуту план меняется
         * (2,2,2,2,1,1 → 2,2,2,1,1,1), уровень тот же — строка одна. Иначе
         * буфер лога на 500 строк забивался бы за поездку.
         */
        levels.put(Seat.DRIVER, 3);
        store.snapshots.put(Seat.DRIVER,
                SeatObserverTest.snapshot(Seat.DRIVER, "3", 1, -15.0, -15.0, -15.0));
        ModelShadow shadow = shadow();
        shadow.update(0L, WALL, levels, true, air(0L, -15.0, null));
        shadow.update(60_000L, WALL + 60_000L, levels, true, air(60_000L, -15.0, null));
        assertEquals(Integer.valueOf(2), shadow.estimate(Seat.DRIVER).level);
        assertEquals(1, decisions(Seat.DRIVER).size());
    }

    @Test
    public void failureDisablesShadowWithoutThrowing() {
        store.failing = true;
        ModelShadow shadow = shadow();
        shadow.update(0L, WALL, levels, true, air(0L, 10.0, null));
        shadow.update(30_000L, WALL, levels, true, air(30_000L, 10.0, null));
        assertNull(shadow.estimate(Seat.DRIVER));
        assertEquals(1, containing(log, "фоновая модель отключена").size());
        assertFalse(containing(log, "диск").isEmpty());
    }

    @Test
    public void confirmedReductionInvalidatesAnEarlierHigherPlan() {
        store.snapshots.put(Seat.DRIVER,
                SeatObserverTest.snapshot(Seat.DRIVER, "3", 1, -25, -25, -25));
        levels.put(Seat.DRIVER, 3);
        ModelShadow shadow = shadow();
        shadow.update(0L, WALL, levels, true, air(0L, -25.0, null));
        assertEquals(Integer.valueOf(3), shadow.estimate(Seat.DRIVER).level);
        levels.put(Seat.DRIVER, 0);
        shadow.update(15_000L, WALL + 15_000L, levels, true, air(15_000L, -25.0, null));
        assertEquals(Integer.valueOf(0), shadow.estimate(Seat.DRIVER).level);
    }

    @Test
    public void startingOtherSeatDoesNotRecomputeThisSeatsRegularPlan() {
        store.snapshots.put(Seat.DRIVER,
                SeatObserverTest.snapshot(Seat.DRIVER, "0", 1, -25, -25, -25));
        levels.put(Seat.DRIVER, 0);
        ModelShadow shadow = shadow();
        shadow.update(0L, WALL, levels, true, air(0L, -25.0, null));
        shadow.requestDecision(Seat.PASSENGER);
        shadow.update(15_000L, WALL + 15_000L, levels, true, air(15_000L, 5.0, null));
        assertEquals(Integer.valueOf(3), shadow.estimate(Seat.DRIVER).level);
    }

    @Test
    public void invalidAirDoesNotPoisonObserverOrPreventRecovery() {
        ModelShadow shadow = shadow();
        levels.put(Seat.DRIVER, 0);
        shadow.update(0L, WALL, levels, true, air(0L, Double.NaN, -15.0));
        assertNull(shadow.estimate(Seat.DRIVER));
        shadow.update(15_000L, WALL + 15_000L, levels, true,
                air(15_000L, -15.0, Double.POSITIVE_INFINITY));
        shadow.update(30_000L, WALL + 30_000L, levels, true, air(30_000L, -15.0, -15.0));
        assertNotNull(shadow.estimate(Seat.DRIVER));
        assertTrue(Double.isFinite(shadow.estimate(Seat.DRIVER).bank.high));
        assertTrue(containing(log, "отключена").isEmpty());
    }
}
