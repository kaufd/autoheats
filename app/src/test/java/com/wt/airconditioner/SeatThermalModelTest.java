package com.wt.airconditioner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * Двухузловая тепловая модель подушки. Числа-эталоны взяты из расчётного
 * эталона спецификации ({@code docs/superpowers/specs/2026-09-10-...-reference.py}):
 * Java-порт обязан повторять его траектории, иначе выводы раздела 8
 * спецификации к приложению не относятся.
 *
 * Тесты проверяют поведение модели, а не соответствие настоящему сиденью:
 * параметры пока не откалиброваны.
 */
public class SeatThermalModelTest {

    /** Эталонные числа выписаны с шестью знаками. */
    private static final double TOLERANCE = 1e-6;

    private static SeatThermalModel.State state(double mat, double surface) {
        return new SeatThermalModel.State(mat, surface);
    }

    private static SeatThermalModel.State advance(SeatThermalModel.State from, int level,
            double environment, double minutes) {
        return SeatThermalModel.advance(from, level, environment,
                new SeatThermalModel.Parameters(), minutes);
    }

    @Test(timeout = 2_000L)
    public void rejectsInputsThatWouldHangOrPoisonTheStep() {
        /**
         * Отрицательный a давал отрицательный шаг, и advance не завершался —
         * отсюда timeout. Остальное — ограничения эталона.
         */
        SeatThermalModel.Parameters negative = new SeatThermalModel.Parameters();
        negative.a = -1.0;
        assertRejected(state(20, 20), 3, 0, negative, 1);

        SeatThermalModel.Parameters nan = new SeatThermalModel.Parameters();
        nan.p = Double.NaN;
        assertRejected(state(20, 20), 3, 0, nan, 1);

        SeatThermalModel.Parameters shortDuty = new SeatThermalModel.Parameters();
        shortDuty.duty = new double[] {0.0, 1.0};
        assertRejected(state(20, 20), 3, 0, shortDuty, 1);

        SeatThermalModel.Parameters defaults = new SeatThermalModel.Parameters();
        assertRejected(state(Double.NaN, 20), 3, 0, defaults, 1);
        assertRejected(state(20, 20), 4, 0, defaults, 1);
        assertRejected(state(20, 20), 3, Double.NaN, defaults, 1);
        assertRejected(state(20, 20), 3, 0, defaults, -1);
        assertRejected(state(20, 20), 3, 0, defaults, Double.POSITIVE_INFINITY);
    }

    private static void assertRejected(SeatThermalModel.State from, int level,
            double environment, SeatThermalModel.Parameters parameters, double minutes) {
        try {
            SeatThermalModel.advance(from, level, environment, parameters, minutes);
            fail("вход должен быть отвергнут");
        } catch (IllegalArgumentException expected) {
            // ожидаемо
        }
    }

    @Test
    public void hotMatKeepsWarmingSurfaceWithoutPower() {
        /**
         * Нулевой уровень не подводит мощность, но накопленное тепло мата всё
         * равно уходит в поверхность: она греется после выключения.
         */
        SeatThermalModel.State after = advance(state(45, 35), 0, 0, 1);
        assertEquals(41.266451, after.mat, TOLERANCE);
        assertEquals(37.248728, after.surface, TOLERANCE);
    }

    @Test
    public void lowerSetpointDoesNotCoolTheMat() {
        /**
         * Уставка ограничивает подводимую мощность, а не температуру мата. При
         * спуске 3 → 1 мат в 45 °C остывает сам, а не притягивается к 35 °C:
         * иначе модель охлаждала бы сиденье, чего физически не бывает.
         */
        assertEquals(advance(state(45, 35), 0, 0, 1).mat,
                advance(state(45, 35), 1, 0, 1).mat, TOLERANCE);
    }

    @Test
    public void strongerLevelNeverGivesColderSurface() {
        double previous = Double.NEGATIVE_INFINITY;
        for (int level = 0; level <= 3; level++) {
            double surface = advance(state(45, 35), level, 0, 1).surface;
            assertTrue("уровень " + level + " охладил поверхность", surface >= previous);
            previous = surface;
        }
    }

    @Test
    public void matStopsAtSetpointWithoutOvershoot() {
        /**
         * Проверка «T₁ < S — полная мощность, иначе ноль» на весь 15-секундный
         * такт дала бы перелёт с 44.9 до 54.24 °C. Ограничивается добавляемая
         * за шаг энергия, поэтому мат выходит ровно на уставку.
         */
        assertEquals(45.0, advance(state(44.9, 35), 3, 0, 0.25).mat, TOLERANCE);
    }

    @Test
    public void conservesEnergyWithoutHeatingAndLosses() {
        /**
         * Без нагрева и внешнего обмена сохраняется T₁ + T₂/r при любом
         * соотношении теплоёмкостей: r = 1 — гипотеза, а не единица.
         */
        for (double ratio : new double[] {0.5, 1.0, 2.0}) {
            SeatThermalModel.Parameters isolated = new SeatThermalModel.Parameters();
            isolated.r = ratio;
            isolated.air = 0;
            isolated.body = 0;
            SeatThermalModel.State start = state(45, 10);
            SeatThermalModel.State end = SeatThermalModel.advance(start, 0, 0, isolated, 30);
            assertEquals(start.mat + start.surface / ratio,
                    end.mat + end.surface / ratio, 1e-9);
        }
    }

    @Test
    public void coolsToTheEnvironment() {
        SeatThermalModel.State end = advance(state(45, 35), 0, -15, 1000);
        assertEquals(-15.0, end.mat, 1e-5);
        assertEquals(-15.0, end.surface, 1e-5);
    }

    @Test
    public void occupantHeatFlowsBothWays() {
        /**
         * Человек нагревает холодное сиденье и отводит тепло от горячего —
         * знак cᵦ·(B − T₂) меняется.
         */
        SeatThermalModel.Parameters occupied = new SeatThermalModel.Parameters();
        occupied.body = 0.08;
        assertTrue(SeatThermalModel.advance(state(10, 10), 0, 10, occupied, 0.25).surface > 10);
        assertTrue(SeatThermalModel.advance(state(40, 40), 0, 40, occupied, 0.25).surface < 40);
    }

    @Test
    public void reproducesReferenceTrajectory() {
        /**
         * Переключения 3/2/0/1 из раздела 8 спецификации. Сравнивается конец
         * траектории, накопивший все четыре участка.
         */
        SeatThermalModel.State seat = state(-10, -10);
        seat = advance(seat, 3, -10, 3);
        seat = advance(seat, 2, -10, 3);
        seat = advance(seat, 0, -10, 3);
        seat = advance(seat, 1, -10, 6);
        assertEquals(32.056090, seat.surface, TOLERANCE);
    }
}
