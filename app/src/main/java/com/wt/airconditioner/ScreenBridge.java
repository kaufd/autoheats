package com.wt.airconditioner;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Всё, что сервис знает про экран: буфер лога, подписка Activity и накопленное
 * состояние для реплея при её открытии.
 *
 * Отдельно от сервиса: экран приходит и уходит, а состояние обязано его
 * пережить, — и правила реплея («что показывать, если ответа от Car ещё нет»)
 * это единственная логика здесь, которую можно проверить на JVM.
 *
 * Ни одной ссылки на Android.
 */
final class ScreenBridge implements LogSink {

    private final LogBuffer logs = new LogBuffer();

    /** Текущий уровень сиденья: реплей отдаёт его же, что и экран спросил бы сам. */
    private final Function<Seat, Integer> levelOf;

    /** null — экран не подключён. */
    private SeatHeatService.UiListener listener;

    /** null — готовность ещё не известна; реплеить такое нельзя. */
    private Boolean hvacReady;
    private Double lastCelsius;

    /** Последний записанный в лог raw: события приходят чаще, чем меняется значение. */
    private Integer loggedCabinRaw;

    ScreenBridge(Function<Seat, Integer> levelOf) {
        this.levelOf = levelOf;
    }

    /**
     * Подключает экран и отдаёт ему накопленное состояние. Реплей и подписка на
     * лог разведены: состояние живёт в главном потоке, откуда и приходит этот
     * вызов, а лог пополняют колбэки Car — он идёт под замком LogBuffer.
     *
     * @param listener null — экран закрылся
     */
    List<String> attach(SeatHeatService.UiListener listener) {
        this.listener = listener;
        if (listener == null) {
            logs.unsubscribe();
            return new ArrayList<>();
        }
        /**
         * null — ответа от Car ещё нет. Реплей false выглядел бы на экране как
         * вердикт «HVAC недоступен», хотя подключение просто не завершилось.
         */
        if (hvacReady != null) {
            listener.onHvacReady(hvacReady);
        }
        if (lastCelsius != null) {
            listener.onCabinTemperature(lastCelsius);
        }
        for (Seat seat : Seat.values()) {
            listener.onSeatLevel(seat, levelOf.apply(seat));
        }
        return logs.subscribe(listener::onLogLine);
    }

    @Override
    public void log(String message) {
        logs.append(message);
    }

    /** Чистит источник лога, а не только его представление в Activity. */
    void clearLogs() {
        logs.clear();
    }

    void hvacReady(boolean ready) {
        hvacReady = ready;
        SeatHeatService.UiListener screen = listener;
        if (screen != null) {
            screen.onHvacReady(ready);
        }
    }

    /** Готовность или null, если ответа от Car ещё не было. */
    Boolean hvacReady() {
        return hvacReady;
    }

    /** Готовность снова неизвестна: после разрыва связи реплей соврал бы. */
    void forgetHvacReady() {
        hvacReady = null;
    }

    /**
     * Температура салона на экран и в лог. В лог — только при изменении:
     * события идут и от подписки, и от снимков раз в полминуты, а буфер — 500
     * строк. Ряд калибровки пишет каждое значение независимо, там пропусков
     * не будет.
     */
    void cabinTemperature(double celsius, int raw) {
        if (loggedCabinRaw == null || loggedCabinRaw != raw) {
            loggedCabinRaw = raw;
            log("температура: raw=" + raw + " → " + Temperature.celsiusText(celsius));
        }
        showTemperature(celsius);
    }

    /**
     * Температура без записи в лог: отладочная подстановка пишет свою строку
     * сама, и raw у неё нет.
     */
    void showTemperature(double celsius) {
        lastCelsius = celsius;
        SeatHeatService.UiListener screen = listener;
        if (screen != null) {
            screen.onCabinTemperature(celsius);
        }
    }

    /** Уровень изменился — не важно, вручную, каскадом или выключением. */
    void seatLevel(Seat seat, int level) {
        SeatHeatService.UiListener screen = listener;
        if (screen != null) {
            screen.onSeatLevel(seat, level);
        }
    }
}
