package com.wt.airconditioner;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Кольцевой буфер диагностики: на голове нет adb, экран — единственный вывод, а
 * буфер нужен, чтобы открытый позже экран увидел происходившее до него.
 *
 * Отдельно от сервиса, иначе подрезку и атомарность подписки не проверить: они
 * жили бы внутри Service, который на JVM не поднять.
 */
final class LogBuffer {

    /** Сколько строк держим. Больше на экране головы всё равно не пролистать. */
    static final int CAPACITY = 500;

    interface Listener {
        void onLogLine(String line);
    }

    private final Deque<String> lines = new ArrayDeque<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    private Listener listener;

    /**
     * Приходит из любого потока: строки пишут и колбэки Car, и главный. Замок
     * общий с subscribe — иначе строка, добавленная между снимком и подпиской,
     * ушла бы на экран дважды или не ушла бы вовсе.
     */
    void append(String message) {
        String line = timeFormat.format(new Date()) + "  " + message;
        synchronized (lines) {
            if (lines.size() >= CAPACITY) {
                lines.removeFirst();
            }
            lines.addLast(line);
            if (listener != null) {
                listener.onLogLine(line);
            }
        }
    }

    /** Подписка и снимок — одной операцией, см. append. */
    List<String> subscribe(Listener listener) {
        synchronized (lines) {
            this.listener = listener;
            return new ArrayList<>(lines);
        }
    }

    void unsubscribe() {
        synchronized (lines) {
            this.listener = null;
        }
    }

    void clear() {
        synchronized (lines) {
            lines.clear();
        }
    }

    /** Только для тестов: сколько строк сейчас в буфере. */
    int size() {
        synchronized (lines) {
            return lines.size();
        }
    }
}
