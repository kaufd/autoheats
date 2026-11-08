package com.wt.airconditioner;

import static org.junit.Assert.*;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Реальная модель, подтверждения и часы: проверяем путь до команды автомобилю. */
public class ModelHeatDriverTest {
    private static final long WALL = 1_757_000_000_000L;
    private final FakeScheduler scheduler = new FakeScheduler();
    private final Map<Seat, Integer> levels = new EnumMap<>(Seat.class);
    private final Map<Seat, String> snapshots = new EnumMap<>(Seat.class);
    private final List<Integer> commands = new ArrayList<>();
    private final List<Integer> passengerCommands = new ArrayList<>();
    private final List<String> logs = new ArrayList<>();
    private Double cabin = -25.0;
    private Double outside;
    private boolean on = true;
    private boolean storageFailure;
    private boolean reject;
    private boolean freshAir = true;
    private long updates;

    private final ModelShadow model = new ModelShadow(new ModelShadow.Store() {
        public String load(Seat seat) { return snapshots.get(seat); }
        public void save(Map<Seat, String> saved) {
            if (storageFailure) throw new IllegalStateException("disk");
            snapshots.putAll(saved);
        }
    }, logs::add);
    private final ModelHeatDriver driver =
            new ModelHeatDriver(scheduler, model, this::refresh, logs::add);

    public ModelHeatDriverTest() {
        for (Seat seat : Seat.values()) {
            snapshots.put(seat, SeatObserverTest.snapshot(seat, "0", 1, -25, -25, -25));
            levels.put(seat, 0);
        }
    }

    private void refresh() {
        updates++;
        long now = scheduler.nowMs();
        long received = freshAir ? now : 0;
        Air air = new Air(cabin, cabin == null ? null : received,
                outside, outside == null ? null : received);
        model.update(now, WALL + now, levels, on, air);
        driver.apply(now, levels, on, air);
    }

    private boolean command(Seat seat, int level) {
        (seat == Seat.DRIVER ? commands : passengerCommands).add(level);
        if (reject) return false;
        levels.put(seat, level);
        // Как setSeatHeat: подтверждение пишет CSV и синхронно обновляет модель.
        refresh();
        return true;
    }

    private void start(Seat seat) {
        driver.start(seat, level -> command(seat, level));
    }

    @Test public void autoStartsFromTheModelAndDoesNotCommandManualPassenger() {
        start(Seat.DRIVER);
        assertEquals(Arrays.asList(3), commands);
        assertTrue(passengerCommands.isEmpty());
        assertEquals(1, scheduler.pendingCount());
        long before = updates;
        scheduler.elapseMillis(14_999);
        assertEquals(before, updates);
        scheduler.elapseMillis(1);
        assertTrue(updates > before);
        assertEquals(Arrays.asList(3), commands);
    }

    @Test public void warmCabinImmediatelyRemovesThirdLevel() {
        start(Seat.DRIVER);
        cabin = 5.0;
        driver.setTemperature(5.0);
        assertEquals(Arrays.asList(3, 2), commands);
        cabin = -25.0;
        driver.setTemperature(-25.0);
        assertEquals("cached plan must not bypass dwell", Arrays.asList(3, 2), commands);
    }

    @Test public void productThresholdStopsImmediatelyAndDoesNotReheatFromCachedPlan() {
        start(Seat.DRIVER);
        cabin = 10.0;
        refresh();
        assertEquals(Arrays.asList(3, 0), commands);
        cabin = -25.0;
        refresh();
        scheduler.elapseMillis(15_000);
        assertEquals(Arrays.asList(3, 0), commands);
    }

    @Test public void thresholdUsesEnvironmentIncludingOutside() {
        cabin = 12.0;
        outside = -25.0;
        start(Seat.DRIVER);
        assertEquals(Arrays.asList(2), commands);
    }

    @Test public void missingAirAndMissingModelTurnExistingHeatOff() {
        cabin = null;
        levels.put(Seat.DRIVER, 3);
        start(Seat.DRIVER);
        assertEquals(Arrays.asList(0), commands);
    }

    @Test public void staleAirStopsOnTheFifteenSecondTick() {
        freshAir = false;
        start(Seat.DRIVER);
        scheduler.elapseMillis(120_000);
        assertTrue(levels.get(Seat.DRIVER) > 0);
        scheduler.elapseMillis(15_000);
        assertEquals(Integer.valueOf(0), levels.get(Seat.DRIVER));
    }

    @Test public void failedModelStopsHeatWithoutTimerCascadeFallback() {
        start(Seat.DRIVER);
        storageFailure = true;
        scheduler.elapseMillis(15_000);
        assertEquals(Arrays.asList(3, 0), commands);
        storageFailure = false;
        scheduler.elapse(5);
        assertEquals(Arrays.asList(3, 0), commands);
    }

    @Test public void rejectedWriteIsRetriedWithoutInventingConfirmation() {
        reject = true;
        start(Seat.DRIVER);
        refresh();
        assertEquals(Arrays.asList(3), commands);
        assertEquals(Integer.valueOf(0), levels.get(Seat.DRIVER));
        scheduler.elapseMillis(15_000);
        assertEquals(Arrays.asList(3, 3), commands);
        reject = false;
        scheduler.elapseMillis(15_000);
        assertEquals(Arrays.asList(3, 3, 3), commands);
        assertEquals(Integer.valueOf(3), levels.get(Seat.DRIVER));
    }

    @Test public void rejectedOffIsRetriedAndOverridesPendingPositiveCommand() {
        start(Seat.DRIVER);
        reject = true;
        cabin = null;
        refresh();
        assertEquals(Integer.valueOf(3), levels.get(Seat.DRIVER));
        refresh();
        assertEquals(Arrays.asList(3, 0), commands);
        reject = false;
        scheduler.elapseMillis(15_000);
        assertEquals(Arrays.asList(3, 0, 0), commands);
        assertEquals(Integer.valueOf(0), levels.get(Seat.DRIVER));
    }

    @Test public void missingAirOverridesRejectedHeatWithoutWaitingForRetry() {
        reject = true;
        start(Seat.DRIVER);
        // Уровень неизвестен: ноль тоже требуется подтвердить в автомобиле.
        levels.remove(Seat.DRIVER);
        cabin = null;
        refresh();
        assertEquals(Arrays.asList(3, 0), commands);
    }

    @Test public void heatingCanResumeAfterZeroAndTheDwell() {
        start(Seat.DRIVER);
        cabin = 10.0;
        refresh();
        assertEquals(Integer.valueOf(0), levels.get(Seat.DRIVER));
        cabin = -25.0;
        scheduler.elapseMillis(165_000);
        assertEquals(Integer.valueOf(0), levels.get(Seat.DRIVER));
        scheduler.elapseMillis(15_000);
        assertTrue("zero is not the end of auto", levels.get(Seat.DRIVER) > 0);
    }

    @Test public void hotSeatModelSendsOffEvenInColdAir() {
        snapshots.put(Seat.DRIVER,
                SeatObserverTest.snapshot(Seat.DRIVER, "3", 1, -25, 45, 42));
        levels.put(Seat.DRIVER, 3);
        start(Seat.DRIVER);
        assertEquals(Arrays.asList(0), commands);
        assertTrue(logs.stream().anyMatch(line -> line.contains("перегрев в прогнозе")));
    }

    @Test public void presetRunsItsFullScheduleIndependentlyOfModelAndTemperature() {
        start(Seat.DRIVER);
        driver.setTemperature(-25.0);
        driver.start(Seat.DRIVER, level -> command(Seat.DRIVER, level),
                new PresetSettings(10, 1, 1, 1));
        commands.clear();
        cabin = 25.0;
        driver.setTemperature(25.0);
        scheduler.elapse(3);
        assertEquals(Arrays.asList(2, 1, 0), commands);
        assertEquals(0, scheduler.pendingCount());
    }

    @Test public void autoCancelsPresetTimerAndManualCancelsAutoTimer() {
        driver.setTemperature(-25.0);
        driver.start(Seat.DRIVER, level -> command(Seat.DRIVER, level),
                new PresetSettings(10, 1, 1, 1));
        start(Seat.DRIVER);
        assertEquals(1, scheduler.pendingCount());
        driver.stop(Seat.DRIVER);
        commands.clear();
        scheduler.elapse(5);
        refresh();
        assertTrue(commands.isEmpty());
        assertEquals(0, scheduler.pendingCount());
    }

    @Test public void twoSeatsShareTickAndStopIndependently() {
        start(Seat.DRIVER);
        start(Seat.PASSENGER);
        assertFalse(passengerCommands.isEmpty());
        assertEquals(1, scheduler.pendingCount());
        driver.stop(Seat.DRIVER);
        commands.clear();
        cabin = null;
        scheduler.elapseMillis(15_000);
        assertTrue(commands.isEmpty());
        assertEquals(Integer.valueOf(0), levels.get(Seat.PASSENGER));
        driver.stopAll();
        assertEquals(0, scheduler.pendingCount());
    }

    @Test public void ignitionOffDoesNotSendModelCommandsBeforeServiceShutdown() {
        on = false;
        start(Seat.DRIVER);
        scheduler.elapseMillis(15_000);
        assertTrue(commands.isEmpty());
        on = true;
        refresh();
        assertFalse(commands.isEmpty());
        on = false;
        int count = commands.size();
        refresh();
        driver.stopAll();
        scheduler.elapse(5);
        assertEquals(count, commands.size());
        assertEquals(0, scheduler.pendingCount());
    }
}
