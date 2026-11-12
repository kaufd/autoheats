package com.wt.airconditioner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Что экран получает при открытии и что попадает в лог. Правила реплея до сих
 * пор держались комментариями: Service на JVM не поднять, и проверить их было
 * негде.
 */
public class ScreenBridgeTest {

    /** Записывает всё, что мост отдал экрану, в порядке поступления. */
    private static final class Screen implements SeatHeatService.UiListener {
        final List<String> events = new ArrayList<>();

        @Override
        public void onLogLine(String line) {
            events.add("log " + line);
        }

        @Override
        public void onHvacReady(boolean ready) {
            events.add("ready " + ready);
        }

        @Override
        public void onCabinTemperature(double celsius) {
            events.add("temp " + celsius);
        }

        @Override
        public void onSeatLevel(Seat seat, int level) {
            events.add("level " + seat.name() + " " + level);
        }
    }

    private final Map<Seat, Integer> levels = new EnumMap<>(Seat.class);
    private final ScreenBridge bridge = new ScreenBridge(seat -> {
        Integer level = levels.get(seat);
        return level == null ? 0 : level;
    });
    private final Screen screen = new Screen();

    @Test
    public void replaysNothingBeforeCarAnswers() {
        /**
         * Реплей false выглядел бы на экране как вердикт «HVAC недоступен»,
         * хотя подключение просто не завершилось; температуры тоже ещё нет.
         */
        bridge.attach(screen);
        assertEquals(java.util.Arrays.asList("level DRIVER 0", "level PASSENGER 0"),
                screen.events);
    }

    @Test
    public void replaysAccumulatedStateToALateScreen() {
        bridge.hvacReady(true);
        bridge.cabinTemperature(-3.0, 78);
        levels.put(Seat.DRIVER, 2);

        bridge.attach(screen);
        assertTrue(screen.events.contains("ready true"));
        assertTrue(screen.events.contains("temp -3.0"));
        assertTrue(screen.events.contains("level DRIVER 2"));
    }

    @Test
    public void forgottenReadinessIsNotReplayed() {
        /** После разрыва связи старое значение соврало бы экрану. */
        bridge.hvacReady(true);
        bridge.forgetHvacReady();

        bridge.attach(screen);
        assertTrue(screen.events.toString(), screen.events.stream()
                .noneMatch(event -> event.startsWith("ready")));
    }

    @Test
    public void repeatedRawIsNotLoggedTwice() {
        /**
         * События идут и от подписки, и от снимков раз в полминуты, а буфер —
         * 500 строк. На экран температура уходит каждый раз: там она не копится.
         */
        bridge.attach(screen);
        bridge.cabinTemperature(-3.0, 78);
        bridge.cabinTemperature(-3.0, 78);
        bridge.cabinTemperature(-2.5, 79);

        /** Строку лога LogBuffer предваряет отметкой времени — ищем по вхождению. */
        assertEquals(2, countContaining(screen.events, "температура: raw="));
        assertEquals(3, count(screen.events, "temp "));
    }

    @Test
    public void detachedScreenStopsReceivingButStateSurvives() {
        bridge.attach(screen);
        bridge.attach(null);
        bridge.hvacReady(true);
        bridge.cabinTemperature(-3.0, 78);
        int afterDetach = screen.events.size();

        Screen reopened = new Screen();
        bridge.attach(reopened);
        assertEquals(afterDetach, screen.events.size());
        assertTrue(reopened.events.contains("ready true"));
        assertTrue(reopened.events.contains("temp -3.0"));
    }

    private static int count(List<String> events, String prefix) {
        int found = 0;
        for (String event : events) {
            if (event.startsWith(prefix)) {
                found++;
            }
        }
        return found;
    }

    private static int countContaining(List<String> events, String part) {
        int found = 0;
        for (String event : events) {
            if (event.contains(part)) {
                found++;
            }
        }
        return found;
    }
}
