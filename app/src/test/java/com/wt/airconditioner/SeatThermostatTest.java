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
    public void frostStartsAtTheTopLevel() {
        SeatThermostat.Decision decision = SeatThermostat.choose(frozenSeat(), 0, 3.0, -15.0);
        assertEquals(3, decision.level);
        assertEquals("план 3,3,3,3,3,3", decision.reason);
    }

    @Test
    public void coldSeatInWarmCabinAlsoStartsAtTheTopLevel() {
        /**
         * Требование владельца (26.11.2026): холодное сиденье без тройки не
         * разогревается. Прежний запрет тройки при салоне выше нуля снят;
         * отпускает её накопленное в мате тепло.
         */
        List<SeatThermostat.Scenario> warm = Collections.singletonList(scenario(5, 5, 5));
        SeatThermostat.Decision decision = SeatThermostat.choose(warm, 0, 3.0, 5.0);
        assertEquals(3, decision.level);
        assertEquals("план 3,3,3,0,0,0", decision.reason);
    }

    @Test
    public void guardFollowsTheProfileHotThreshold() {
        /**
         * Ограничитель — порог «жарко» профиля, а не общие 39 °C: та же
         * поверхность перегревает профиль с порогом 40 и не перегревает
         * профиль с порогом 44.3, у которого «жарко» наступает позже.
         */
        SeatThermostat.Scenario common = scenario(41, 41, 0);
        SeatThermostat.Scenario late = scenario(41, 41, 0);
        late.parameters.hotCelsius = 44.3;
        assertTrue(SeatThermostat.mustCoast(Collections.singletonList(common), 1));
        assertFalse(SeatThermostat.mustCoast(Collections.singletonList(late), 1));
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
         * на минуту хотя бы в одном сценарии выходит за порог «жарко» (40 °C).
         */
        assertTrue(SeatThermostat.mustCoast(
                Collections.singletonList(scenario(45, 42, 0)), 3));
        assertFalse(SeatThermostat.mustCoast(frozenSeat(), 1));
    }

    @Test
    public void calibratedBankAgreesOnTheTopLevel() {
        /**
         * До подбора порогов «жарко» широкий банк в морозе снижал тройку до
         * двойки (план 2,2,2,2,1,1): быстрый профиль упирался в общие 39 °C.
         * С порогами, подобранными по поездкам, профили одинаково предсказывают
         * момент «жарко», и банк, как и один профиль, держит тройку.
         */
        SeatThermostat.Decision decision = SeatThermostat.choose(hypotheticalBank(), 0, 3.0, -15.0);
        assertEquals(3, decision.level);
        assertEquals("план 3,3,3,3,3,3", decision.reason);
    }

    /**
     * Банк из 18 профилей раздела 6 спецификации с подобранными порогами «жарко» — тот, что
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
