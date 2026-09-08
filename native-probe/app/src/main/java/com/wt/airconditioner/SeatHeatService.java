package com.wt.airconditioner;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Foreground-сервис: владеет соединением с Car и выключает подогрев при
 * выключении зажигания.
 *
 * Соединение живёт здесь, а не в Activity, потому что подогрев обязан
 * работать со свёрнутым приложением: на Android 13+ без foreground-сервиса
 * процесс убивают, и автоматика молча перестаёт срабатывать.
 *
 * AccessibilityService, который во Flutter-версии поднимал background isolate,
 * здесь не нужен: сервис плюс RECEIVE_BOOT_COMPLETED делают то же самое
 * штатными средствами.
 */
public class SeatHeatService extends Service implements CarHvacProbe.Listener {

    private static final String CHANNEL_ID = "seat_heat";
    private static final int NOTIFICATION_ID = 888;

    /** Сколько строк лога держим для UI. Экран — единственный вывод: adb нет. */
    private static final int LOG_CAPACITY = 500;

    /** Слушатель UI; сервис работает и без него. */
    public interface UiListener {
        void onLogLine(String line);

        void onHvacReady(boolean ready);

        void onCabinTemperature(double celsius, int raw);
    }

    public class LocalBinder extends Binder {
        public SeatHeatService getService() {
            return SeatHeatService.this;
        }
    }

    private final IBinder binder = new LocalBinder();
    private final Deque<String> logLines = new ArrayDeque<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    private CarHvacProbe probe;
    private UiListener uiListener;
    private boolean hvacReady;
    private Double lastCelsius;
    private Integer lastRaw;

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(NOTIFICATION_ID, buildNotification("Подключение к автомобилю…"));
        onLog("сервис запущен");
        probe = new CarHvacProbe(this, this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Пересоздаём сервис, если система его убила: подогрев должен пережить
        // нехватку памяти.
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        if (probe != null) {
            probe.disconnect();
        }
        super.onDestroy();
    }

    // --- API для Activity ---

    public void setUiListener(UiListener listener) {
        this.uiListener = listener;
        if (listener == null) {
            return;
        }
        // Отдаём накопленное: события до открытия экрана не должны пропасть.
        listener.onHvacReady(hvacReady);
        if (lastCelsius != null) {
            listener.onCabinTemperature(lastCelsius, lastRaw);
        }
    }

    public List<String> logSnapshot() {
        synchronized (logLines) {
            return new ArrayList<>(logLines);
        }
    }

    public void setSeatHeat(boolean isDriver, int level) {
        probe.setSeatHeat(isDriver, level);
    }

    public void readCabinTemperature() {
        probe.readCabinTemperature();
    }

    // --- CarHvacProbe.Listener ---

    @Override
    public void onLog(String message) {
        String line = timeFormat.format(new Date()) + "  " + message;
        synchronized (logLines) {
            if (logLines.size() >= LOG_CAPACITY) {
                logLines.removeFirst();
            }
            logLines.addLast(line);
        }
        UiListener listener = uiListener;
        if (listener != null) {
            listener.onLogLine(line);
        }
    }

    @Override
    public void onHvacReady(boolean ready) {
        hvacReady = ready;
        updateNotification(ready ? "Подогрев сидений активен" : "Нет связи с автомобилем");
        UiListener listener = uiListener;
        if (listener != null) {
            listener.onHvacReady(ready);
        }
    }

    @Override
    public void onCabinTemperature(double celsius, int raw) {
        lastCelsius = celsius;
        lastRaw = raw;
        onLog("температура: raw=" + raw + " → " + String.format(Locale.US, "%.1f", celsius) + " °C");
        UiListener listener = uiListener;
        if (listener != null) {
            listener.onCabinTemperature(celsius, raw);
        }
    }

    @Override
    public void onIgnition(boolean on) {
        if (on) {
            return;
        }
        // Зажигание выключено — гасим оба сиденья, чтобы подогрев не остался
        // включённым до следующей поездки.
        onLog("зажигание выключено → выключаю оба сиденья");
        probe.setSeatHeat(true, 0);
        probe.setSeatHeat(false, 0);
    }

    // --- уведомление ---

    private Notification buildNotification(String text) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Подогрев сидений", NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        PendingIntent content = PendingIntent.getActivity(
                this, 0, new Intent(this, MainActivity.class),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? PendingIntent.FLAG_IMMUTABLE
                        : 0);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        return builder
                .setContentTitle("AutoHeat")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(content)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification(text));
        }
    }
}
