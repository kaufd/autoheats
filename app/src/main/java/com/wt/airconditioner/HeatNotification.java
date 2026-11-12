package com.wt.airconditioner;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * Постоянное уведомление foreground-сервиса. Без него на Android 13+ процесс
 * убивают со свёрнутым приложением, и автоматика молча перестаёт срабатывать.
 *
 * Отдельно от сервиса: канал, PendingIntent и совместимость с версиями Android
 * меняются по своим причинам и к подогреву отношения не имеют.
 */
final class HeatNotification {

    static final int ID = 888;
    private static final String CHANNEL_ID = "seat_heat";

    private final Context context;

    /** Канал и PendingIntent живут сколько сервис — заводятся один раз. */
    private PendingIntent contentIntent;

    HeatNotification(Context context) {
        this.context = context;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Подогрев сидений", NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    /** Для startForeground: сервис обязан показать уведомление сразу. */
    Notification build(String text) {
        if (contentIntent == null) {
            contentIntent = PendingIntent.getActivity(
                    context, 0, new Intent(context, MainActivity.class),
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                            ? PendingIntent.FLAG_IMMUTABLE
                            : 0);
        }

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);

        return builder
                .setContentTitle("AutoHeat")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .build();
    }

    void update(String text) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(ID, build(text));
        }
    }
}
