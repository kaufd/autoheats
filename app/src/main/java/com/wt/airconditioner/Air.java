package com.wt.airconditioner;

/**
 * Показания воздуха с временем их получения приложением. Салон и улица едут
 * вместе, потому что вместе и нужны: окружение подушки считается от обоих.
 *
 * Здесь же решается, достоверно ли показание, — это правило тепловой модели
 * (раздел 2 спецификации), а не сервиса. Сервис отдаёт сырьё и время, модель
 * сама решает, годится ли оно.
 *
 * Ни одной ссылки на Android: время в миллисекундах монотонных часов, которые
 * подаёт вызывающий.
 */
final class Air {

    /**
     * Допустимый возраст показания с момента получения приложением. При опросе
     * раз в 30 секунд превышение значит несколько подряд неудачных чтений или
     * sentinel, а не неподвижный салон.
     */
    static final long MAX_AGE_MS = 120_000L;

    private final Double cabinCelsius;
    private final Long cabinAtMs;
    private final Double outsideCelsius;
    private final Long outsideAtMs;

    Air(Double cabinCelsius, Long cabinAtMs, Double outsideCelsius, Long outsideAtMs) {
        this.cabinCelsius = cabinCelsius;
        this.cabinAtMs = cabinAtMs;
        this.outsideCelsius = outsideCelsius;
        this.outsideAtMs = outsideAtMs;
    }

    /** Достоверный салон на этот момент или null. */
    Double cabin(long nowMs) {
        return reliable(cabinCelsius, cabinAtMs, nowMs);
    }

    /** Достоверная улица на этот момент или null. */
    Double outside(long nowMs) {
        return reliable(outsideCelsius, outsideAtMs, nowMs);
    }

    /**
     * Значение, если оно достоверно по возрасту, иначе null. Внутреннюю
     * устарелость головы отсюда не видно — от неё защищает пересчёт при смене
     * значения в SeatObserver.
     *
     * package-private ради теста: собирать Air целиком ради одной проверки
     * возраста не за чем.
     */
    static Double reliable(Double value, Long receivedAtMs, long nowMs) {
        if (value == null || receivedAtMs == null) {
            return null;
        }
        long age = nowMs - receivedAtMs;
        return age >= 0 && age <= MAX_AGE_MS ? value : null;
    }
}
