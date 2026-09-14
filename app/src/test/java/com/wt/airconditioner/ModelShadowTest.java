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
        public void save(Seat seat, String snapshot) {
            if (failing) {
                throw new IllegalStateException("диск");
            }
            snapshots.put(seat, snapshot);
        }
    }

    private final MemoryStore store = new MemoryStore();
    private final List<String> log = new ArrayList<>();
    private final Map<Seat, Integer> levels = new EnumMap<>(Seat.class);

    private ModelShadow shadow() {
        return new ModelShadow(store, log::add);
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
        shadow.update(0L, WALL, levels, true, null, null);
        assertNull(shadow.model(Seat.DRIVER));
        assertEquals("годный снимок", store.snapshots.get(Seat.DRIVER));

        shadow.update(30_000L, WALL, levels, true, 10.0, null);
        assertNotNull(shadow.model(Seat.DRIVER));
        assertTrue(containing(log, "отключена").isEmpty());
    }

    @Test
    public void restoresBothSeatsAndSavesSnapshots() {
        ModelShadow shadow = shadow();
        shadow.update(0L, WALL, levels, false, 10.0, null);
        assertNotNull(shadow.model(Seat.DRIVER));
        assertNotNull(shadow.model(Seat.PASSENGER));
        assertNotNull(store.snapshots.get(Seat.DRIVER));
        assertNotNull(store.snapshots.get(Seat.PASSENGER));
        assertEquals(2, containing(log, "неизвестная история").size());

        ModelShadow restarted = shadow();
        restarted.update(0L, WALL, levels, false, 10.0, null);
        assertEquals(2, containing(log, "снимок, перерыв").size());
    }

    @Test
    public void decidesOnlyWhileIgnitionIsOn() {
        ModelShadow shadow = shadow();
        shadow.update(0L, WALL, levels, false, -15.0, null);
        assertNull(shadow.model(Seat.DRIVER).level);
        assertTrue(decisions(Seat.DRIVER).isEmpty());

        shadow.update(1_000L, WALL, levels, true, -15.0, null);
        assertNotNull(shadow.model(Seat.DRIVER).level);
        assertEquals(1, decisions(Seat.DRIVER).size());

        shadow.update(2_000L, WALL, levels, false, -15.0, null);
        assertNull(shadow.model(Seat.DRIVER).level);
    }

    @Test
    public void regularDecisionWaitsAMinute() {
        /**
         * Салон пропал: решение, посчитанное сразу, дало бы ноль и строку в
         * логе. Через 30 секунд его ещё нет, через минуту — есть.
         */
        ModelShadow shadow = shadow();
        shadow.update(0L, WALL, levels, true, -15.0, null);
        int before = decisions(Seat.PASSENGER).size();

        shadow.update(30_000L, WALL, levels, true, null, null);
        assertEquals(before, decisions(Seat.PASSENGER).size());

        shadow.update(60_000L, WALL, levels, true, null, null);
        List<String> after = decisions(Seat.PASSENGER);
        assertEquals(before + 1, after.size());
        assertTrue(after.get(after.size() - 1).contains("салон недостоверен"));
        assertEquals(Integer.valueOf(0), shadow.model(Seat.PASSENGER).level);
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
        shadow.update(0L, WALL, levels, true, 25.0, null);
        shadow.update(30_000L, WALL, levels, true, 25.0, null);
        shadow.update(45_000L, WALL, levels, true, 25.0, null);
        assertEquals(1, containing(decisions(Seat.DRIVER), "перегрев в прогнозе").size());
        assertEquals(Integer.valueOf(0), shadow.model(Seat.DRIVER).level);
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
        shadow.update(0L, WALL, levels, true, -15.0, null);
        shadow.update(60_000L, WALL + 60_000L, levels, true, -15.0, null);
        assertEquals(Integer.valueOf(2), shadow.model(Seat.DRIVER).level);
        assertEquals(1, decisions(Seat.DRIVER).size());
    }

    @Test
    public void failureDisablesShadowWithoutThrowing() {
        store.failing = true;
        ModelShadow shadow = shadow();
        shadow.update(0L, WALL, levels, true, 10.0, null);
        shadow.update(30_000L, WALL, levels, true, 10.0, null);
        assertNull(shadow.model(Seat.DRIVER));
        assertEquals(1, containing(log, "фоновая модель отключена").size());
        assertFalse(containing(log, "диск").isEmpty());
    }
}
