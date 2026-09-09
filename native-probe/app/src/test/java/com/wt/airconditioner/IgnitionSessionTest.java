package com.wt.airconditioner;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

/**
 * Жизненный цикл поездки. Проверяется тестом, потому что в машине эти переходы
 * не отладить: лестница ACC → OFF проходит за доли секунды и уносит с собой
 * процесс, а ошибка выглядит либо как «подогрев погас сам», либо как «сиденья
 * грелись в брошенной машине».
 */
public class IgnitionSessionTest {

    private IgnitionSession session;

    @Before
    public void setUp() {
        session = new IgnitionSession();
    }

    /** Дверь открыли, ГУ поднялось, человек садится — гасить нечего и незачем. */
    @Test
    public void ignitionOffBeforeFirstOnIsIgnored() {
        assertEquals(IgnitionSession.Action.NOTHING, session.onIgnition(false));
        session.seatsHeating();
        assertEquals("подогрев включён руками — он не наше дело до первого ON",
                IgnitionSession.Action.NOTHING, session.onIgnition(false));
    }

    /** Обычная поездка: ON запускает каскад, OFF гасит сиденья один раз. */
    @Test
    public void tripStartsHeatThenShutsDownOnce() {
        assertEquals(IgnitionSession.Action.START_HEAT, session.onIgnition(true));
        assertEquals("повторное ON каскад не перезапускает",
                IgnitionSession.Action.NOTHING, session.onIgnition(true));

        session.seatsHeating();
        assertEquals(IgnitionSession.Action.SHUTDOWN, session.onIgnition(false));
        session.shutdownResult(true);
        assertEquals("вторая ступень лестницы ACC → OFF молчит",
                IgnitionSession.Action.NOTHING, session.onIgnition(false));
    }

    /**
     * Заправка: заглушил, через три минуты завёл. Каскад обязан пойти заново —
     * сиденья погасил предыдущий OFF, и без перезапуска они останутся
     * холодными до конца поездки.
     */
    @Test
    public void restartWithinSameSessionStartsHeatAgain() {
        session.onIgnition(true);
        session.seatsHeating();
        session.onIgnition(false);
        session.shutdownResult(true);

        assertEquals(IgnitionSession.Action.START_HEAT, session.onIgnition(true));
    }

    /**
     * Неподтверждённое выключение оставляет поездку открытой: следующая ступень
     * лестницы — единственный шанс повторить попытку.
     */
    @Test
    public void unconfirmedShutdownIsRetriedOnNextEvent() {
        session.onIgnition(true);
        session.seatsHeating();
        assertEquals(IgnitionSession.Action.SHUTDOWN, session.onIgnition(false));
        session.shutdownResult(false);

        assertEquals(IgnitionSession.Action.SHUTDOWN, session.onIgnition(false));
    }

    /**
     * Голова поспала на короткой стоянке и проснулась. Процесс жив, но в машину
     * сели заново: первое «не ON» после пробуждения — не конец старой поездки.
     */
    @Test
    public void wakeUpStartsFreshSession() {
        session.onIgnition(true);
        session.seatsHeating();

        session.onWakeUp();
        assertEquals(IgnitionSession.Action.NOTHING, session.onIgnition(false));
        assertEquals(IgnitionSession.Action.START_HEAT, session.onIgnition(true));
    }
}
