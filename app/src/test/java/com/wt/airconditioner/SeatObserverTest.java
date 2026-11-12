package com.wt.airconditioner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/**
 * Наблюдатель: оценка сиденья нижней и верхней границей по фактическим
 * уровням, воздуху и зажиганию, и снимок между перезапусками процесса.
 *
 * Эталон траекторий — сама тепловая модель, сверенная с расчётом
 * спецификации в SeatThermalModelTest. Здесь проверяется, какие уровни,
 * окружения и контакты наблюдатель ей подаёт и как хранит результат.
 */
public class SeatObserverTest {

    private static final double TOLERANCE = 1e-9;
    private static final long WALL = 1_757_000_000_000L;
    private static final long MINUTE_MS = 60_000L;

    /**
     * Снимок с одинаковыми нижней и верхней границей во всех историях —
     * известное начальное состояние. Формат: версия; настенное время; уровень
     * (пусто — неизвестен); зажигание; окружение; базовый профиль; истории,
     * каждая «нижний мат,нижняя поверхность,верхний мат,верхняя поверхность».
     */
    static String snapshot(Seat seat, String level, int ignition, double environment,
            double mat, double surface) {
        int histories = seat == Seat.DRIVER ? 12 : 18;
        String state = mat + "," + surface + "," + mat + "," + surface;
        StringBuilder text = new StringBuilder("1;" + WALL + ";" + level + ";" + ignition
                + ";" + environment + ";" + state);
        for (int i = 0; i < histories; i++) {
            text.append(';').append(state);
        }
        return text.toString();
    }

    /** Восстановление без перерыва; уровень и зажигание — текущие, с этого момента. */
    private static SeatObserver restored(String snapshot, double cabin, Integer level,
            boolean ignitionOn) {
        return SeatObserver.restore(Seat.DRIVER, snapshot, WALL, 0L, cabin, null, level,
                ignitionOn);
    }

    private static double baseSurface(double mat, double surface, int level, double environment,
            double minutes) {
        return SeatThermalModel.advance(new SeatThermalModel.State(mat, surface), level,
                environment, new SeatThermalModel.Parameters(), minutes).surface;
    }

    @Test
    public void unknownHistoryCoversColdAirAndHotSeat() {
        /**
         * Раздел 3 спецификации: L = min(салон, улица) − 3, верх — не ниже 45.
         * Без этого после потери снимка сиденье сочли бы заведомо холодным.
         */
        SeatObserver observer = SeatObserver.restore(
                Seat.PASSENGER, null, WALL, 0L, 10.0, 13.0, 0, false);
        assertEquals(7.0, observer.base().low, TOLERANCE);
        assertEquals(45.0, observer.base().high, TOLERANCE);
        assertEquals(7.0, observer.bank().low, TOLERANCE);
        assertEquals(45.0, observer.bank().high, TOLERANCE);
    }

    @Test
    public void knownStateFollowsConfirmedLevelOverTheInterval() {
        /**
         * Уровень промежутка — тот, что был подтверждён в его начале: новый
         * уровень, пришедший событием, действует только дальше.
         */
        SeatObserver observer = restored(
                snapshot(Seat.DRIVER, "3", 1, 20.0, 20.0, 20.0), 20.0, 3, true);
        observer.observe(5 * MINUTE_MS, 0, true, 20.0, null);
        double expected = baseSurface(20, 20, 3, 20, 5);
        assertEquals(expected, observer.base().low, TOLERANCE);
        assertEquals(expected, observer.base().high, TOLERANCE);
    }

    @Test
    public void parkingWithChangedAirSplitsBoundsByEnvelope() {
        /**
         * Воздух за стоянку неизвестен: нижняя граница остывает к меньшему из
         * окружений начала и конца, верхняя — к большему.
         */
        SeatObserver observer = restored(
                snapshot(Seat.DRIVER, "0", 0, 20.0, 30.0, 30.0), 20.0, 0, false);
        observer.observe(30 * MINUTE_MS, 0, false, 10.0, null);
        assertEquals(baseSurface(30, 30, 0, 10, 30), observer.base().low, TOLERANCE);
        assertEquals(baseSurface(30, 30, 0, 20, 30), observer.base().high, TOLERANCE);
    }

    @Test
    public void unknownLevelHeatsOnlyTheUpperBound() {
        SeatObserver observer = restored(
                snapshot(Seat.DRIVER, "", 0, 20.0, 20.0, 20.0), 20.0, null, false);
        observer.observe(5 * MINUTE_MS, 0, false, 20.0, null);
        assertEquals(20.0, observer.base().low, TOLERANCE);
        assertEquals(baseSurface(20, 20, 3, 20, 5), observer.base().high, TOLERANCE);
    }

    @Test
    public void levelWithoutIgnitionHeatsOnlyTheUpperBound() {
        /**
         * Раздел 2 дизайна: подогрев обесточивается вместе с зажиганием, и
         * только подтверждённое выключение даёт ноль. Выключить не удалось —
         * нижняя граница не греется, верхняя честно греет.
         */
        SeatObserver observer = restored(
                snapshot(Seat.DRIVER, "2", 0, 20.0, 20.0, 20.0), 20.0, 2, false);
        observer.observe(5 * MINUTE_MS, 2, false, 20.0, null);
        assertEquals(20.0, observer.base().low, TOLERANCE);
        assertEquals(baseSurface(20, 20, 2, 20, 5), observer.base().high, TOLERANCE);
    }

    @Test
    public void longUnconfirmedHeatingIsUnknownHistory() {
        /**
         * Ночь с уровнем, который не удалось выключить: грел ли подогрев и
         * сколько — неизвестно. Досчитывать ночь нагрева незачем и дорого на
         * главном потоке — это неизвестная история от воздуха перерыва.
         */
        SeatObserver observer = restored(
                snapshot(Seat.DRIVER, "2", 0, 10.0, 10.0, 10.0), 10.0, 2, false);
        observer.observe(8 * 60 * MINUTE_MS, 2, false, 10.0, null);
        assertEquals(7.0, observer.base().low, TOLERANCE);
        assertEquals(45.0, observer.base().high, TOLERANCE);
    }

    @Test
    public void bodyContactActsOnlyWhileIgnitionIsOn() {
        /**
         * При выключенном зажигании кресло пустое: сиденье в равновесии с
         * воздухом так и остаётся. При ON тело (32…35 °C) греет его во всех
         * историях водителя.
         */
        SeatObserver parked = restored(
                snapshot(Seat.DRIVER, "0", 0, 20.0, 20.0, 20.0), 20.0, 0, false);
        parked.observe(10 * MINUTE_MS, 0, false, 20.0, null);
        assertEquals(20.0, parked.bank().low, TOLERANCE);
        assertEquals(20.0, parked.bank().high, TOLERANCE);

        SeatObserver seated = restored(
                snapshot(Seat.DRIVER, "0", 1, 20.0, 20.0, 20.0), 20.0, 0, true);
        seated.observe(10 * MINUTE_MS, 0, true, 20.0, null);
        assertTrue(seated.bank().low > 20.0);
    }

    @Test
    public void emptyPassengerStaysInTheBankWhileDriverIsAssumedSeated() {
        /**
         * Допущение раздела 2: водитель при ON на месте — пустого контакта у
         * него нет. У пассажира пустое кресло обычно, и замер 14.09 показал,
         * что оно греется.
         */
        for (SeatThermalModel.Parameters profile : SeatObserver.profilesWhileOn(Seat.DRIVER)) {
            assertTrue(profile.body > 0);
        }
        List<SeatThermalModel.Parameters> passenger = SeatObserver.profilesWhileOn(Seat.PASSENGER);
        assertEquals(18, passenger.size());
        int empty = 0;
        for (SeatThermalModel.Parameters profile : passenger) {
            if (profile.body == 0) {
                empty++;
            }
        }
        assertEquals(6, empty);
    }

    @Test
    public void scenariosCarryBothBoundsWithCurrentContact() {
        SeatObserver observer = SeatObserver.restore(
                Seat.DRIVER, null, WALL, 0L, 10.0, null, 0, true);
        List<SeatThermostat.Scenario> scenarios = observer.scenarios();
        assertEquals(24, scenarios.size());
        double coldest = Double.POSITIVE_INFINITY;
        double hottest = Double.NEGATIVE_INFINITY;
        for (SeatThermostat.Scenario scenario : scenarios) {
            assertTrue(scenario.parameters.body > 0);
            assertEquals(10.0, scenario.environment, TOLERANCE);
            coldest = Math.min(coldest, scenario.state.surface);
            hottest = Math.max(hottest, scenario.state.surface);
        }
        assertEquals(7.0, coldest, TOLERANCE);
        assertEquals(45.0, hottest, TOLERANCE);
    }

    @Test
    public void longParkingStopsOnlyWhenSeatMetTheAir() {
        /**
         * Сорок часов стоянки досчитываются с остановкой по сближению, а не
         * целиком: результат обязан совпасть с полным расчётом до 0.01 °C.
         */
        SeatObserver observer = restored(
                snapshot(Seat.DRIVER, "0", 0, 10.0, 45.0, 45.0), 10.0, 0, false);
        observer.observe(40 * 60 * MINUTE_MS, 0, false, 10.0, null);
        double full = baseSurface(45, 45, 0, 10, 40 * 60);
        assertEquals(full, observer.base().low, 0.01);
        assertEquals(full, observer.base().high, 0.01);
        /**
         * Базовый профиль остывает быстро и ранней остановки не заметит.
         * Самый медленный профиль банка (r = 2, cₐ вдвое меньше) остывает с
         * постоянной около 150 минут: за 40 часов и он сравнялся с воздухом.
         */
        assertEquals(10.0, observer.bank().low, 0.01);
        assertEquals(10.0, observer.bank().high, 0.01);
    }

    @Test
    public void staleAirAfterLongParkingIsReplacedWithinSettleWindow() {
        /**
         * Замер 14.09: после пробуждения голова ~50 секунд отдаёт салон прошлой
         * сессии. Перерыв, досчитанный по нему, пересчитывается, когда пришёл
         * свежий воздух, — вместе с тем, что случилось после перерыва (здесь
         * ON и тройка).
         */
        SeatObserver observer = restored(
                snapshot(Seat.DRIVER, "0", 0, 20.0, 20.0, 20.0), 20.0, 0, false);
        observer.observe(40 * 60 * MINUTE_MS, 3, true, 20.0, null);
        observer.observe(40 * 60 * MINUTE_MS + MINUTE_MS, 3, true, 10.0, null);

        SeatThermalModel.Parameters base = new SeatThermalModel.Parameters();
        SeatThermalModel.State night = SeatThermalModel.advance(
                new SeatThermalModel.State(20, 20), 0, 10, base, 40 * 60);
        double expected = SeatThermalModel.advance(night, 3, 10, base, 1).surface;
        assertEquals(expected, observer.base().low, 0.02);
    }

    @Test
    public void airChangeAfterSettleWindowIsOrdinaryWeather() {
        SeatObserver observer = restored(
                snapshot(Seat.DRIVER, "0", 0, 27.5, 27.5, 27.5), 27.5, 0, false);
        observer.observe(40 * 60 * MINUTE_MS, 0, false, 27.5, null);
        observer.observe(40 * 60 * MINUTE_MS + 5 * MINUTE_MS, 0, false, 10.0, null);
        assertEquals(baseSurface(27.5, 27.5, 0, 10, 5), observer.base().low, TOLERANCE);
    }

    @Test
    public void unknownHistoryFromStaleAirIsRebuiltWithinSettleWindow() {
        SeatObserver observer = SeatObserver.restore(
                Seat.DRIVER, null, WALL, 0L, 27.5, null, 0, false);
        observer.observe(MINUTE_MS, 0, false, 10.0, null);
        assertEquals(baseSurface(7, 7, 0, 10, 1), observer.base().low, TOLERANCE);
    }

    @Test
    public void snapshotGapFromStaleAirIsReplacedWithinSettleWindow() {
        SeatObserver observer = SeatObserver.restore(Seat.DRIVER,
                snapshot(Seat.DRIVER, "0", 0, 27.5, 27.5, 27.5),
                WALL + 40 * 60 * MINUTE_MS, 0L, 27.5, null, 0, false);
        observer.observe(MINUTE_MS, 0, false, 10.0, null);
        assertEquals(10.0, observer.base().low, 0.02);
        assertEquals(27.5, observer.base().high, 0.02);
    }

    @Test
    public void snapshotRestoresStatesAcrossProcessRestart() {
        SeatObserver before = restored(
                snapshot(Seat.DRIVER, "2", 1, 15.0, 15.0, 15.0), 15.0, 2, true);
        before.observe(4 * MINUTE_MS, 2, true, 15.0, null);

        SeatObserver after = SeatObserver.restore(Seat.DRIVER, before.encode(WALL),
                WALL, 0L, 15.0, null, 2, true);
        assertEquals(before.base().low, after.base().low, TOLERANCE);
        assertEquals(before.bank().low, after.bank().low, TOLERANCE);
        assertEquals(before.bank().high, after.bank().high, TOLERANCE);
    }

    @Test
    public void restoreCountsTheGapWithSnapshotLevel() {
        /**
         * Процесс перезапущен через 5 минут поездки: перерыв досчитывается с
         * уровнем из снимка, а не с текущим.
         */
        SeatObserver observer = SeatObserver.restore(Seat.DRIVER,
                snapshot(Seat.DRIVER, "3", 1, 20.0, 20.0, 20.0),
                WALL + 5 * MINUTE_MS, 0L, 20.0, null, 0, true);
        assertEquals(baseSurface(20, 20, 3, 20, 5), observer.base().low, TOLERANCE);
    }

    @Test
    public void longGapAfterSnapshotInTripIsUnknownHistory() {
        /**
         * Голова гаснет при OFF вместе с процессом, и последний снимок — из
         * поездки. Когда выключили зажигание и кто сидел, неизвестно: ночь «с
         * человеком на тройке» сделала бы промёрзшее сиденье тёплым.
         */
        SeatObserver observer = SeatObserver.restore(Seat.DRIVER,
                snapshot(Seat.DRIVER, "3", 1, 10.0, 40.0, 40.0),
                WALL + 8 * 60 * MINUTE_MS, 0L, 10.0, null, 0, true);
        assertEquals(7.0, observer.base().low, TOLERANCE);
        assertEquals(45.0, observer.base().high, TOLERANCE);
        assertTrue(observer.origin(), observer.origin().startsWith("неизвестная история"));
    }

    @Test
    public void unusableSnapshotMeansUnknownHistory() {
        String valid = snapshot(Seat.DRIVER, "0", 0, 10.0, 30.0, 30.0);
        String[] unusable = {
                "мусор",
                "2" + valid.substring(1),
                valid.replace("30.0", "NaN"),
                snapshot(Seat.PASSENGER, "0", 0, 10.0, 30.0, 30.0),
        };
        for (String snapshot : unusable) {
            SeatObserver observer = restored(snapshot, 10.0, 0, false);
            assertEquals(snapshot, 7.0, observer.base().low, TOLERANCE);
            assertEquals(snapshot, 45.0, observer.base().high, TOLERANCE);
        }
        SeatObserver clockWentBack = SeatObserver.restore(
                Seat.DRIVER, valid, WALL - MINUTE_MS, 0L, 10.0, null, 0, false);
        assertEquals(7.0, clockWentBack.base().low, TOLERANCE);
        assertEquals(45.0, clockWentBack.base().high, TOLERANCE);
    }
}
