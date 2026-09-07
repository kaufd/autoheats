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

    /**
     * Сколько ждать onServiceConnected, прежде чем признать подключение
     * несостоявшимся. Без этого статус «Подключение к Car…» может висеть
     * вечно, а пробник обязан давать однозначный ответ.
     */
    private static final long CONNECT_TIMEOUT_MS = 10_000L;

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
                        publishTemperature(value.getValue());
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
            mainHandler.postDelayed(connectTimeout, CONNECT_TIMEOUT_MS);
        } catch (Throwable t) {
            log("ОШИБКА подключения к Car: " + t);
            notifyReady(false);
        }
    }

    private final Runnable connectTimeout = new Runnable() {
        @Override
        public void run() {
            if (!isHvacReady()) {
                log("ТАЙМАУТ " + (CONNECT_TIMEOUT_MS / 1000)
                        + " с: onServiceConnected не пришёл, HVAC не поднялся");
                notifyReady(false);
            }
        }
    };

    private void initHvacManager() {
        try {
            CarHvacManager manager = (CarHvacManager) car.getCarManager(Car.HVAC_SERVICE);
            if (manager == null) {
                log("ОШИБКА: getCarManager(HVAC_SERVICE) вернул null");
                notifyReady(false);
                return;
            }
            hvacManager = manager;
            log("CarHvacManager готов");
            notifyReady(true);

            // Подписка на события — отдельно и не критично: чтение по кнопке и
            // установка уровня от неё не зависят. Если она упадёт, пробник
            // должен остаться рабочим и сказать об этом, а не притвориться
            // мёртвым, оставив isHvacReady() противоречить статусу на экране.
            try {
                manager.registerCallback(hvacCallback);
                log("подписка на события HVAC оформлена");
            } catch (Throwable t) {
                log("подписка на события HVAC не удалась (чтение и запись работают): " + t);
            }

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
        mainHandler.removeCallbacks(connectTimeout);
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

    /**
     * Формула датчика головы: (raw - 84) / 2 = °C. Та же, что в HvacService,
     * вместе с его правилами валидности: значение приходит числом ИЛИ строкой,
     * а raw < 0 — это sentinel «нет данных», а не -42 °C. Для диагностического
     * инструмента показать -42 °C как рабочую температуру хуже, чем не
     * показать ничего: это ложный вывод об исправности датчика.
     */
    private void publishTemperature(Object rawValue) {
        Integer raw = parseRaw(rawValue);
        if (raw == null) {
            log("температура проигнорирована: неожиданный тип значения (" + rawValue + ")");
            return;
        }
        if (raw < 0) {
            log("температура проигнорирована: sentinel raw=" + raw + " (нет данных с датчика)");
            return;
        }
        final int value = raw;
        final double celsius = (value - 84) / 2.0;
        mainHandler.post(() -> listener.onCabinTemperature(celsius, value));
    }

    /** package-private ради юнит-теста: на эмуляторе этот путь не проверить. */
    static Integer parseRaw(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return (int) Double.parseDouble(((String) value).trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private void notifyReady(boolean ready) {
        mainHandler.post(() -> listener.onHvacReady(ready));
    }

    private void log(String message) {
        mainHandler.post(() -> listener.onLog(message));
    }
}
