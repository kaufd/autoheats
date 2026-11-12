package com.wt.airconditioner;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.io.File;

/**
 * Периодический снимок состояния: освежить показания, пересчитать фоновую
 * модель, записать строку ряда калибровки.
 *
 * Вынесено из сервиса: тик, окно стоянки и файл ряда меняются по своим
 * причинам и к соединению с Car отношения не имеют. Здесь же держится порядок
 * «модель обновляется до строки» — раньше он жил в комментариях трёх вызывающих
 * и в одном месте уже был нарушен.
 *
 * Строки вне тика (смена уровня, событие зажигания) идут сюда же: ряд один, и
 * решать, писать ли в него, должен один класс.
 */
final class CalibrationSampler {

    /** Откуда берётся содержимое строки. */
    interface Source {
        /** Прочитать автомобиль заново; только перед тиковой строкой. */
        void refresh();

        /**
         * Пересчитать фоновую модель. Отдельно от sample(): строка обязана
         * показать оценку уже с учётом события, и порядок держит вызывающий.
         */
        void updateModel();

        /** Состояние на этот момент. */
        CalibrationLog.Sample sample(String reason);
    }

    /**
     * Шаг ряда. Постоянная времени подушки — около двух минут, так что минутные
     * отсчёты уже размывают разгон, а чаще нет смысла: датчик воздуха всё равно
     * обновляется медленнее.
     */
    private static final long SAMPLE_INTERVAL_MS = 30_000L;

    /**
     * Сколько ряд пишется после выключения зажигания. Первые минуты голова ещё
     * отдаёт салон, дальше — только 255 всю стоянку. Опрос в это время пользы не
     * несёт, а в ночь 12→13.09 голова восемь часов не спала, и без опроса на
     * стоянке вопрос «не мы ли её держим» снимается сам.
     */
    static final long PARKED_SAMPLE_MS = 5 * 60_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Source source;
    private final LogSink logSink;

    /** null — внешнее хранилище недоступно: тик идёт, файл не пишется. */
    private final CalibrationLog log;

    private boolean running;
    private boolean failureLogged;

    /**
     * elapsedRealtime первого «не ON» подряд; null — зажигание ON или ещё не
     * известно. Неизвестное стоянкой не считается: без подписки на зажигание
     * ряд иначе встал бы посреди поездки.
     */
    private Long parkedSinceMs;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            source.refresh();
            record("tick");
            if (!keepsSampling(parkedSinceMs, SystemClock.elapsedRealtime())) {
                running = false;
                log("стоянка: ряд калибровки на паузе до зажигания ON");
                return;
            }
            handler.postDelayed(this, SAMPLE_INTERVAL_MS);
        }
    };

    /**
     * Ряд пишется во внутреннюю память, а не на флешку: он должен вестись
     * всегда, а не только когда носитель воткнут. На флешку его выгружает
     * кнопка «Сохранить» — там же, где диагностический лог.
     *
     * @param directory null — внешнее хранилище недоступно; тик всё равно
     *        нужен: он освежает воздух и ведёт фоновую модель
     */
    CalibrationSampler(File directory, Source source, LogSink logSink) {
        this.source = source;
        this.logSink = logSink;
        this.log = directory == null ? null : new CalibrationLog(directory);
        log(log == null
                ? "ВНИМАНИЕ: ряд калибровки не ведётся — внешнее хранилище недоступно"
                : "ряд калибровки: " + log.file().getName());
    }

    /**
     * Время стоянки — по elapsedRealtime, а не по uptime: сон головы в него
     * входит, и первый тик после ночи останавливает ряд, а не пишет ещё пять
     * минут бодрствования. Отдельно — ради теста.
     */
    static boolean keepsSampling(Long parkedSinceMs, long nowMs) {
        return parkedSinceMs == null || nowMs - parkedSinceMs < PARKED_SAMPLE_MS;
    }

    void start() {
        if (running) {
            return;
        }
        running = true;
        handler.postDelayed(tick, SAMPLE_INTERVAL_MS);
    }

    void stop() {
        handler.removeCallbacks(tick);
        running = false;
    }

    /**
     * Зажигание сменилось. Отсчёт стоянки — от первого «не ON»: голова шлёт их
     * пачкой (ACC, затем OFF), и каждое следующее не должно продлевать запись.
     */
    void onIgnition(boolean on) {
        if (on) {
            parkedSinceMs = null;
            start();
        } else if (parkedSinceMs == null) {
            parkedSinceMs = SystemClock.elapsedRealtime();
        }
    }

    /** Строка ряда с пересчитанной перед ней моделью. */
    void record(String reason) {
        source.updateModel();
        if (log == null) {
            return;
        }
        if (!log.append(source.sample(reason)) && !failureLogged) {
            failureLogged = true;
            log("ВНИМАНИЕ: ряд калибровки не пишется — " + log.file());
        }
    }

    private void log(String message) {
        if (logSink != null) {
            logSink.log(message);
        }
    }
}
