package com.wt.airconditioner;

import android.car.Car;
import android.car.VehicleAreaSeat;
import android.car.VehiclePropertyIds;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.hvac.CarHvacManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/**
 * Минимальный мост к android.car HVAC: подключение, чтение температуры салона,
 * установка уровня подогрева сидений.
 *
 * Последовательность подключения повторяет CarAvcManagerUtils из рабочего
 * плагина (Car.createCar с ServiceConnection → connect() → getCarManager в
 * onServiceConnected). Отличий быть не должно: это единственный путь,
 * про который известно, что он работает на голове Changan.
 */
public class CarHvacProbe {

    /** Температура в салоне. Вычислено из HvacPropertyIds плагина. */
    private static final int ID_HVAC_IN_OUT_TEMP = 675289370;

    /** VehicleAreaInOutCAR.InOutCAR_INSIDE — зона «внутри салона». */
    private static final int AREA_INSIDE = 1;

    public interface Listener {
        void onLog(String message);

        /** Готовность CarHvacManager изменилась. */
        void onHvacReady(boolean ready);

        /** Событие температуры салона от подписки на HVAC. */
        void onCabinTemperature(double celsius, int raw);
    }

    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Car car;
    private CarHvacManager hvacManager;

    private final CarHvacManager.CarHvacEventCallback hvacCallback =
            new CarHvacManager.CarHvacEventCallback() {
                @Override
                public void onChangeEvent(CarPropertyValue value) {
                    if (value.getPropertyId() == ID_HVAC_IN_OUT_TEMP
                            && value.getAreaId() == AREA_INSIDE) {
                        publishTemperature(toInt(value.getValue()));
                    }
                }

                @Override
                public void onErrorEvent(int propertyId, int zone) {
                    log("HVAC onErrorEvent: property=" + propertyId + ", zone=" + zone);
                }
            };

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            log("onServiceConnected: " + name);
            initHvacManager();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            log("onServiceDisconnected");
            if (hvacManager != null) {
                try {
                    hvacManager.unregisterCallback(hvacCallback);
                } catch (Exception e) {
                    log("unregisterCallback error: " + e);
                }
                hvacManager = null;
            }
            notifyReady(false);
        }
    };

    public CarHvacProbe(Context context, Listener listener) {
        this.listener = listener;

        log("Car.createCar…");
        // Throwable, а не Exception: если android.car отсутствует или подтянулся
        // стаб, прилетит NoClassDefFoundError / RuntimeException("Stub!"). Пробник
        // обязан показать это на экране, а не умереть — экран и есть его результат.
        try {
            car = Car.createCar(context, serviceConnection);
            if (car == null) {
                log("ОШИБКА: Car.createCar вернул null — android.car недоступен");
                notifyReady(false);
                return;
            }
            car.connect();
            log("car.connect() вызван, ждём onServiceConnected");
        } catch (Throwable t) {
            log("ОШИБКА подключения к Car: " + t);
            notifyReady(false);
        }
    }

    private void initHvacManager() {
        try {
            hvacManager = (CarHvacManager) car.getCarManager(Car.HVAC_SERVICE);
            if (hvacManager == null) {
                log("ОШИБКА: getCarManager(HVAC_SERVICE) вернул null");
                notifyReady(false);
                return;
            }
            hvacManager.registerCallback(hvacCallback);
            log("CarHvacManager готов, подписка оформлена");
            notifyReady(true);
            readCabinTemperature();
        } catch (Throwable t) {
            // Ожидаемое место падения, если пакет не в whitelist головы:
            // здесь прилетает SecurityException "… is not in white list!".
            log("ОШИБКА initHvacManager: " + t);
            notifyReady(false);
        }
    }

    public boolean isHvacReady() {
        return car != null && car.isConnected() && hvacManager != null;
    }

    /** Разовое чтение температуры салона; результат уходит в listener. */
    public void readCabinTemperature() {
        if (!isHvacReady()) {
            log("readCabinTemperature пропущено: HVAC не готов");
            return;
        }
        try {
            publishTemperature(hvacManager.getIntProperty(ID_HVAC_IN_OUT_TEMP, AREA_INSIDE));
        } catch (Exception e) {
            log("ОШИБКА чтения температуры: " + e);
        }
    }

    /** level 0..3, где 0 — выключено. */
    public void setSeatHeat(boolean isDriver, int level) {
        String seat = isDriver ? "водитель" : "пассажир";
        if (!isHvacReady()) {
            log("setSeatHeat(" + seat + ", " + level + ") пропущено: HVAC не готов");
            return;
        }
        int area = isDriver ? VehicleAreaSeat.SEAT_MAIN_DRIVER : VehicleAreaSeat.SEAT_PASSENGER;
        try {
            hvacManager.setIntProperty(VehiclePropertyIds.HVAC_SEAT_TEMPERATURE, area, level);
            log("setSeatHeat OK: " + seat + " → " + level);
        } catch (Exception e) {
            log("ОШИБКА setSeatHeat(" + seat + ", " + level + "): " + e);
        }
    }

    public void disconnect() {
        try {
            if (hvacManager != null) {
                hvacManager.unregisterCallback(hvacCallback);
                hvacManager = null;
            }
            if (car != null) {
                car.disconnect();
            }
        } catch (Exception e) {
            log("ОШИБКА disconnect: " + e);
        }
    }

    /** Формула датчика головы: (raw - 84) / 2 = °C. Та же, что в HvacService. */
    private void publishTemperature(int raw) {
        final double celsius = (raw - 84) / 2.0;
        mainHandler.post(() -> listener.onCabinTemperature(celsius, raw));
    }

    private static int toInt(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private void notifyReady(boolean ready) {
        mainHandler.post(() -> listener.onHvacReady(ready));
    }

    private void log(String message) {
        mainHandler.post(() -> listener.onLog(message));
    }
}
