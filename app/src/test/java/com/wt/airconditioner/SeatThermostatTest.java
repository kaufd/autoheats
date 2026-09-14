package com.wt.airconditioner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Выбор уровня по тепловой модели. Решения сверяются с расчётным эталоном
 * спецификации: план на горизонте и есть объяснение уровня, поэтому он
 * проверяется целиком, а не только первой минутой.
 *
 * Проверяется регулятор, а не комфорт настоящего сиденья: параметры
 * теплообмена ещё не откалиброваны.
 */
public class SeatThermostatTest {

    private static SeatThermostat.Scenario scenario(double mat, double surface,
            double environment) {
        return new SeatThermostat.Scenario(new SeatThermalModel.State(mat, surface),
                new SeatThermalModel.Parameters(), environment);
    }

    private static List<SeatThermostat.Scenario> frozenSeat() {
        return Collections.singletonList(scenario(-15, -15, -15));
    }

    @Test
    public void frostStartsAtTheTopLevelAndPlansToReleaseIt() {
        SeatThermostat.Decision decision = SeatThermostat.choose(frozenSeat(), 0, 3.0, -15.0);
        assertEquals(3, decision.level);
        assertEquals("план 3,3,3,3,0,0", decision.reason);
    }

    @Test
    public void warmCabinForbidsTheTopLevel() {
        /**
         * Явное предпочтение владельца, а не следствие уравнений: тройка
         * разрешена только при салоне не выше нуля.
         */
        List<SeatThermostat.Scenario> warm = Collections.singletonList(scenario(5, 5, 5));
        assertEquals(2, SeatThermostat.choose(warm, 0, 3.0, 5.0).level);
    }

    @Test
    public void untrustedCabinTurnsHeatingOff() {
        /**
         * Раздел 2 спецификации: при потере достоверного салона подаётся ноль,
         * а не «всё, кроме тройки», — даже в промёрзшем сиденье.
         */
        assertEquals(0, SeatThermostat.choose(frozenSeat(), 2, 3.0, null).level);
        assertEquals(0, SeatThermostat.choose(frozenSeat(), 2, 3.0, Double.NaN).level);
    }

    @Test
    public void emptyBankTurnsHeatingOff() {
        /**
         * Без сценариев любой план выглядел допустимым, и при текущей тройке
         * она сохранялась; промежуточная проверка перегрев тоже не находила.
         */
        List<SeatThermostat.Scenario> none = Collections.emptyList();
        assertEquals(0, SeatThermostat.choose(none, 3, 0.0, -15.0).level);
        assertTrue(SeatThermostat.mustCoast(none, 3));
    }

    @Test
    public void corruptedScenarioTurnsHeatingOff() {
        SeatThermostat.Scenario broken = scenario(-15, -15, -15);
        broken.parameters.a = -1.0;
        List<SeatThermostat.Scenario> bank = Collections.singletonList(broken);
        assertEquals(0, SeatThermostat.choose(bank, 3, 0.0, -15.0).level);
        assertTrue(SeatThermostat.mustCoast(bank, 3));

        List<SeatThermostat.Scenario> nan =
                Collections.singletonList(scenario(Double.NaN, 42, 0));
        assertEquals(0, SeatThermostat.choose(nan, 3, 0.0, 0.0).level);
        assertTrue(SeatThermostat.mustCoast(nan, 3));
    }

    @Test
    public void raisingWaitsForTheDwell() {
        /**
         * Повышение не раньше трёх минут после подтверждённой смены уровня.
         * План показывает, что тройка запланирована на третью минуту, — но
         * применяется только первая минута плана, и через минуту задача
         * решается заново.
         */
        SeatThermostat.Decision decision = SeatThermostat.choose(frozenSeat(), 1, 0.0, -15.0);
        assertEquals(1, decision.level);
        assertEquals("план 1,1,1,3,3,3", decision.reason);
    }

    @Test
    public void raisingIsAllowedAfterTheDwell() {
        assertEquals(3, SeatThermostat.choose(frozenSeat(), 1, 3.0, -15.0).level);
    }

    @Test
    public void overheatedSeatHasNoAdmissiblePlan() {
        /**
         * Понижение разрешено сразу, задержка на него не действует. Ноль не
         * объявляется способным убрать уже накопленный перегрев — модель не
         * умеет охлаждать.
         */
        List<SeatThermostat.Scenario> hot = Collections.singletonList(scenario(45, 42, 0));
        SeatThermostat.Decision decision = SeatThermostat.choose(hot, 3, 0.0, 0.0);
        assertEquals(0, decision.level);
        assertEquals("нет допустимого плана, нагрев выключен", decision.reason);
    }

    @Test
    public void coastingStopsHeatingBeforeTheNextMinute() {
        /**
         * Промежуточная проверка на 15-секундном такте: удержание уровня ещё
         * на минуту хотя бы в одном сценарии выходит за 39 °C.
         */
        assertTrue(SeatThermostat.mustCoast(
                Collections.singletonList(scenario(45, 42, 0)), 3));
        assertFalse(SeatThermostat.mustCoast(frozenSeat(), 1));
    }

    @Test
    public void widestBankIsHeatedMoreCarefully() {
        /**
         * Минимакс по сценариям, а не среднее: в том же морозе широкий набор
         * гипотез снижает уровень с тройки до двойки. Это цена неопределённости
         * параметров — систематический недогрев.
         */
        SeatThermostat.Decision decision = SeatThermostat.choose(hypotheticalBank(), 0, 3.0, -15.0);
        assertEquals(2, decision.level);
        assertEquals("план 2,2,2,2,1,1", decision.reason);
    }

    /**
     * Банк из 18 гипотетических профилей раздела 6 спецификации — тот, что
     * ведёт наблюдатель пассажира: три набора теплообмена × две гипотезы
     * устройства уровней × три профиля контакта. Набор для поиска проблем, не
     * калибровка и не распределение вероятностей.
     */
    private static List<SeatThermostat.Scenario> hypotheticalBank() {
        List<SeatThermostat.Scenario> bank = new ArrayList<>();
        for (SeatThermalModel.Parameters profile : SeatObserver.profilesWhileOn(Seat.PASSENGER)) {
            bank.add(new SeatThermostat.Scenario(
                    new SeatThermalModel.State(-15, -15), profile, -15));
        }
        return bank;
    }
}
