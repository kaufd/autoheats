package com.wt.airconditioner;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;
import android.widget.Toast;

import java.util.List;

/**
 * Жизненный цикл binding отделён от Activity: закрытие экрана не останавливает
 * сервис, а Activity не хранит собственную копию его runtime-состояния.
 */
final class ServiceBindingController implements SeatHeatServiceProvider {

    interface Listener {
        void onConnected(SeatHeatService service, List<String> logSnapshot);

        void onDisconnected();
    }

    private final Activity activity;
    private final SeatHeatService.UiListener uiListener;
    private final Listener listener;

    private SeatHeatService service;
    private boolean bound;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((SeatHeatService.LocalBinder) binder).getService();
            List<String> snapshot = service.setUiListener(uiListener);
            listener.onConnected(service, snapshot);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
            listener.onDisconnected();
        }
    };

    ServiceBindingController(Activity activity, SeatHeatService.UiListener uiListener,
            Listener listener) {
        this.activity = activity;
        this.uiListener = uiListener;
        this.listener = listener;
    }

    void start() {
        Intent intent = new Intent(activity, SeatHeatService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.startForegroundService(intent);
        } else {
            activity.startService(intent);
        }
        bound = activity.bindService(intent, connection, 0);
        if (!bound) {
            Toast.makeText(activity, "Сервис не привязался", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public SeatHeatService get() {
        return service;
    }

    void destroy() {
        if (service != null) {
            service.setUiListener(null);
            service = null;
        }
        if (bound) {
            activity.unbindService(connection);
            bound = false;
        }
    }
}
