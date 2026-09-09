package com.wt.airconditioner;

import android.car.Car;
import android.car.VehicleAreaSeat;
import android.car.VehiclePropertyIds;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.CarSensorEvent;
import android.car.hardware.CarSensorManager;
import android.car.hardware.hvac.CarHvacManager;
import android.car.hardware.power.CarPowerManager;
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

    /**
     * Повторы подписки на зажигание. Без неё теряется автовыключение сидений —
     * единственная защита от подогрева, оставленного в пустой машине, — а
     * отказ сразу после подъёма CarService вполне может оказаться временным.
     * Три попытки с шагом в пять секунд перекрывают запуск головы и при этом
     * не превращаются в бесконечный опрос мёртвого сервиса.
     */
    private static final int SENSOR_RETRIES = 3;
    private static final long SENSOR_RETRY_DELAY_MS = 5_000L;

    /** IgnitionState.IGNITION_STATE_ON — всё остальное считается «зажигание не включено». */
    private static final int IGNITION_STATE_ON = 4;

    /**
     * IgnitionState.IGNITION_STATE_START — проворот стартера. Машину заводят,
     * а не покидают, поэтому состояние пропускается: считать его «выключено»
     * значит гасить подогрев ровно в момент запуска двигателя. Видно в логе с
     * головы: ON → «не ON (5)» → выключили оба сиденья → ON, за одну секунду.
     */
    private static final int IGNITION_STATE_START = 5;

    public interface Listener {
        void onLog(String message);

        /** Готовность CarHvacManager изменилась. */
        void onHvacReady(boolean ready);

        /** Событие температуры салона от подписки на HVAC. */
        void onCabinTemperature(double celsius, int raw);

        /** Событие зажигания; false — любое состояние, кроме ON. */
        void onIgnition(boolean on);

        /**
         * Голова проснулась после сна. Для приложения это то же самое, что
         * запуск: процесс пережил короткую стоянку, но в машину сели заново.
         */
        void onWakeUp();
    }

    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Car car;
    private CarHvacManager hvacManager;

    /**
     * Пришёл ли onServiceConnected. Нужен именно факт коннекта, а не
     * isHvacReady(): без него таймаут не отличит «соединение не поднялось»
     * от «соединение есть, но getCarManager отказал», и назовёт неверную
     * причину поверх настоящей.
     */
    private boolean serviceConnected;

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

    private CarSensorManager sensorManager;
    private CarPowerManager powerManager;

    /** Сколько повторов подписки на зажигание осталось в этом подключении. */
    private int sensorRetriesLeft = SENSOR_RETRIES;

    /**
     * Отложенный повтор подписки — полем, чтобы его можно было снять. Иначе
     * после disconnect() и создания нового пробника (restartCarConnection)
     * старый callback всё равно сработает и полезет к менеджерам мёртвого
     * соединения.
     */
    private final Runnable sensorRetry = new Runnable() {
        @Override
        public void run() {
            // Связь могла оборваться, пока мы ждали: после реконнекта подписку
            // оформит onServiceConnected, и с полным счётчиком попыток.
            if (serviceConnected && sensorManager == null) {
                initSensorManager();
            }
        }
    };

    private final CarSensorManager.OnSensorChangedListener sensorListener =
            new CarSensorManager.OnSensorChangedListener() {
                @Override
                public void onSensorChanged(CarSensorEvent event) {
                    if (event == null
                            || event.sensorType != CarSensorManager.SENSOR_TYPE_IGNITION_STATE) {
                        return;
                    }
                    Boolean on = ignitionOn(event.intValues);
                    if (on == null) {
                        log("зажигание: пропущено — " + skipReason(event.intValues));
                        return;
                    }
                    final boolean ignitionOn = on;
                    log("зажигание: " + (ignitionOn ? "ON" : "не ON (" + event.intValues[0] + ")"));
                    mainHandler.post(() -> listener.onIgnition(ignitionOn));
                }
            };

    /**
     * Зажигание включено только в состоянии ON; ACC, LOCK, OFF и UNDEFINED
     * трактуются как выключенное — правило из
     * BackgroundRuntimeController.handleIgnition. Отличие одно: START не
     * считается выключением, см. IGNITION_STATE_START. null — «состояние не
     * менять»: событие без значения или START.
     */
    static Boolean ignitionOn(int[] intValues) {
        if (intValues == null || intValues.length == 0) {
            return null;
        }
        if (intValues[0] == IGNITION_STATE_START) {
            return null;
        }
        return intValues[0] == IGNITION_STATE_ON;
    }

    /** Почему событие зажигания пропущено — для строки в логе. */
    private static String skipReason(int[] intValues) {
        return intValues == null || intValues.length == 0
                ? "событие без значения"
                : "START (" + intValues[0] + ") — двигатель запускается";
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            serviceConnected = true;
            // Соединение состоялось — сторожевой таймаут больше не нужен.
            // Иначе после разрыва связи он сработает по старому расписанию и
            // объявит несостоявшимся подключение, которое на самом деле было.
            mainHandler.removeCallbacks(connectTimeout);
            log("onServiceConnected: " + name);
            // Новое соединение — новый счёт попыток: предыдущие относились к
            // умершему CarService и ничего не говорят о поднявшемся.
            sensorRetriesLeft = SENSOR_RETRIES;
            initHvacManager();
            // Зажигание поднимаем независимо от исхода HVAC: без него теряется
            // автовыключение сидений, а отказ HVAC (например, пакет не в
            // whitelist) сам по себе не значит, что датчик недоступен.
            initSensorManager();
            initPowerManager();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            log("onServiceDisconnected: жду автоматического переподключения");
            serviceConnected = false;
            // Оба менеджера принадлежат умершему CarService: если оставить
            // ссылки, повторная подписка молча перезапишет их, а старый
            // listener так и останется зарегистрированным на мёртвом инстансе.
            if (hvacManager != null) {
                try {
                    hvacManager.unregisterCallback(hvacCallback);
                } catch (Exception e) {
                    log("unregisterCallback error: " + e);
                }
                hvacManager = null;
            }
            if (sensorManager != null) {
                try {
                    sensorManager.unregisterListener(sensorListener);
                } catch (Exception e) {
                    log("unregisterListener error: " + e);
                }
                sensorManager = null;
            }
            powerManager = null;
            notifyReady(false);
            // Своего реконнекта здесь намеренно нет. Car.createCar биндится с
            // BIND_AUTO_CREATE, поэтому систему держит наш же биндинг: она сама
            // перепривяжется к поднявшемуся CarService и снова вызовет
            // onServiceConnected, где менеджеры оформляются заново. Ручной
            // car.connect() на том же объекте на Android 9 либо бросит
            // IllegalStateException (состояние ещё не DISCONNECTED), либо
            // сделает второй bindService поверх живого биндинга — то есть
            // добавит недетерминированности ровно в тот путь, который должен
            // быть предсказуемым.
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
            if (serviceConnected) {
                // Соединение поднялось; если HVAC при этом не готов, причину
                // уже назвал initHvacManager — вторая строка поверх неё только
                // увела бы диагностику в сторону.
                return;
            }
            log("ТАЙМАУТ " + (CONNECT_TIMEOUT_MS / 1000)
                    + " с: onServiceConnected не пришёл, соединение с CarService не поднялось");
            notifyReady(false);
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

    /**
     * Датчик зажигания — отдельно от HVAC и тоже необязателен: без него
     * подогрев продолжает работать, теряется только автовыключение.
     */
    private void initSensorManager() {
        try {
            CarSensorManager manager = (CarSensorManager) car.getCarManager(Car.SENSOR_SERVICE);
            if (manager == null) {
                retrySensorManager("getCarManager(SENSOR_SERVICE) вернул null");
                return;
            }
            boolean registered = manager.registerListener(sensorListener,
                    CarSensorManager.SENSOR_TYPE_IGNITION_STATE,
                    CarSensorManager.SENSOR_RATE_NORMAL);
            if (!registered) {
                retrySensorManager("подписка отклонена CarSensorManager");
                return;
            }
            sensorManager = manager;
            sensorRetriesLeft = SENSOR_RETRIES;
            log("подписка на зажигание оформлена");
            publishCurrentIgnition(manager);
        } catch (Throwable t) {
            retrySensorManager(String.valueOf(t));
        }
    }

    /**
     * Повторяет подписку на зажигание, пока попытки не кончатся. Исчерпанные
     * попытки — не мелочь в логе, а потеря автовыключения: об этом сказано
     * отдельной строкой, чтобы её было видно среди прочего вывода.
     */
    private void retrySensorManager(String reason) {
        if (sensorRetriesLeft <= 0) {
            log("ВНИМАНИЕ: зажигание недоступно (" + reason
                    + ") — автовыключение сидений не работает");
            return;
        }
        sensorRetriesLeft--;
        log("подписка на зажигание не удалась (" + reason + "), повтор через "
                + (SENSOR_RETRY_DELAY_MS / 1000) + " с");
        mainHandler.removeCallbacks(sensorRetry);
        mainHandler.postDelayed(sensorRetry, SENSOR_RETRY_DELAY_MS);
    }

    /**
     * Текущее состояние зажигания сразу после подписки. Без этого сервис,
     * поднявшийся при уже выключенном зажигании (автозапуск после
     * перезагрузки, реконнект к CarService), дождётся только следующего
     * изменения — а его может не быть, и сиденья останутся включёнными.
     */
    private void publishCurrentIgnition(CarSensorManager manager) {
        try {
            CarSensorEvent event = manager.getLatestSensorEvent(
                    CarSensorManager.SENSOR_TYPE_IGNITION_STATE);
            if (event == null) {
                log("текущее зажигание неизвестно: getLatestSensorEvent вернул null");
                return;
            }
            Boolean on = ignitionOn(event.intValues);
            if (on == null) {
                log("текущее зажигание не определено: " + skipReason(event.intValues));
                return;
            }
            final boolean ignitionOn = on;
            log("текущее зажигание: " + (ignitionOn ? "ON" : "не ON (" + event.intValues[0] + ")"));
            mainHandler.post(() -> listener.onIgnition(ignitionOn));
        } catch (Throwable t) {
            log("текущее зажигание прочитать не удалось: " + t);
        }
    }

    /**
     * Причина, по которой поднялось ГУ, и события засыпания. Отвечает на
     * вопрос, который иначе не проверить: просыпается ли голова при
     * дистанционном запуске двигателя. Если да, `getBootReason()` вернёт
     * REMOTE_START, и подогрев можно готовить к приходу водителя; если нет,
     * приложение в этот момент физически не выполняется.
     *
     * SUSPEND_ENTER / SHUTDOWN_ENTER заодно показывают, засыпает голова или
     * выключается совсем — от этого зависит, доживает ли сервис до
     * возвращения.
     */
    private void initPowerManager() {
        try {
            CarPowerManager manager = (CarPowerManager) car.getCarManager(Car.POWER_SERVICE);
            if (manager == null) {
                log("питание: getCarManager(POWER_SERVICE) вернул null");
                return;
            }
            powerManager = manager;
            log("причина запуска ГУ: " + bootReasonName(manager.getBootReason()));
            manager.setListener(this::onPowerState, mainHandler::post);
            log("подписка на состояние питания оформлена");
        } catch (Throwable t) {
            log("питание недоступно (на работу подогрева не влияет): " + t);
        }
    }

    /**
     * Голова засыпает на первые ~30 минут стоянки и только потом выключается
     * совсем. Значит после короткой остановки процесс не перезапускается, и
     * без этого события «новая посадка» осталась бы незамеченной: сервис
     * считал бы, что поездка не кончалась.
     */
    private void onPowerState(int state) {
        log("состояние питания: " + powerStateName(state));
        if (state == CarPowerManager.CarPowerStateListener.SUSPEND_EXIT) {
            listener.onWakeUp();
        }
    }

    private static String bootReasonName(int reason) {
        switch (reason) {
            case CarPowerManager.BOOT_REASON_USER_POWER_ON:
                return "USER_POWER_ON (кнопка)";
            case CarPowerManager.BOOT_REASON_DOOR_UNLOCK:
                return "DOOR_UNLOCK (разблокировка)";
            case CarPowerManager.BOOT_REASON_TIMER:
                return "TIMER (по таймеру)";
            case CarPowerManager.BOOT_REASON_DOOR_OPEN:
                return "DOOR_OPEN (открыта дверь)";
            case CarPowerManager.BOOT_REASON_REMOTE_START:
                return "REMOTE_START (дистанционный запуск)";
            default:
                return "неизвестна (" + reason + ")";
        }
    }

    private static String powerStateName(int state) {
        switch (state) {
            case CarPowerManager.CarPowerStateListener.SHUTDOWN_CANCELLED:
                return "SHUTDOWN_CANCELLED";
            case CarPowerManager.CarPowerStateListener.SHUTDOWN_ENTER:
                return "SHUTDOWN_ENTER (выключение)";
            case CarPowerManager.CarPowerStateListener.SUSPEND_ENTER:
                return "SUSPEND_ENTER (засыпание)";
            case CarPowerManager.CarPowerStateListener.SUSPEND_EXIT:
                return "SUSPEND_EXIT (пробуждение)";
            default:
                return "неизвестно (" + state + ")";
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

    /**
     * level 0..3, где 0 — выключено. Возвращает, дошла ли запись до
     * автомобиля: вызывающий обязан знать, что выключение сидений не
     * состоялось, — молчаливая потеря этой команды оставляет подогрев
     * включённым до следующей поездки.
     */
    public boolean setSeatHeat(boolean isDriver, int level) {
        String seat = isDriver ? "водитель" : "пассажир";
        if (!isHvacReady()) {
            log("setSeatHeat(" + seat + ", " + level + ") пропущено: HVAC не готов");
            return false;
        }
        int area = isDriver ? VehicleAreaSeat.SEAT_MAIN_DRIVER : VehicleAreaSeat.SEAT_PASSENGER;
        try {
            hvacManager.setIntProperty(VehiclePropertyIds.HVAC_SEAT_TEMPERATURE, area, level);
            log("setSeatHeat OK: " + seat + " → " + level);
            return true;
        } catch (Exception e) {
            log("ОШИБКА setSeatHeat(" + seat + ", " + level + "): " + e);
            return false;
        }
    }

    /**
     * Что стоит на сиденье по мнению автомобиля. null — прочитать не удалось:
     * свойство может оказаться доступным только на запись, и тогда вызывающий
     * остаётся при своём представлении, а не при выдуманном нуле.
     */
    public Integer readSeatHeat(boolean isDriver) {
        String seat = isDriver ? "водитель" : "пассажир";
        if (!isHvacReady()) {
            return null;
        }
        int area = isDriver ? VehicleAreaSeat.SEAT_MAIN_DRIVER : VehicleAreaSeat.SEAT_PASSENGER;
        try {
            int level = hvacManager.getIntProperty(
                    VehiclePropertyIds.HVAC_SEAT_TEMPERATURE, area);
            if (level < 0 || level > 3) {
                log("уровень подогрева (" + seat + ") вне диапазона: " + level);
                return null;
            }
            return level;
        } catch (Exception e) {
            log("уровень подогрева (" + seat + ") прочитать не удалось: " + e);
            return null;
        }
    }

    public void disconnect() {
        mainHandler.removeCallbacks(connectTimeout);
        // Отложенный повтор подписки принадлежит этому соединению: после
        // restartCarConnection он полез бы к менеджерам, которых уже нет.
        mainHandler.removeCallbacks(sensorRetry);
        serviceConnected = false;
        // Каждый шаг под своим catch: падение первого unregister не должно
        // оставить car соединённым, а callback — зарегистрированным.
        if (sensorManager != null) {
            try {
                sensorManager.unregisterListener(sensorListener);
            } catch (Exception e) {
                log("ОШИБКА unregisterListener: " + e);
            }
            sensorManager = null;
        }
        if (hvacManager != null) {
            try {
                hvacManager.unregisterCallback(hvacCallback);
            } catch (Exception e) {
                log("ОШИБКА unregisterCallback: " + e);
            }
            hvacManager = null;
        }
        if (powerManager != null) {
            try {
                powerManager.clearListener();
            } catch (Exception e) {
                log("ОШИБКА clearListener: " + e);
            }
            powerManager = null;
        }
        if (car != null) {
            try {
                car.disconnect();
            } catch (Exception e) {
                log("ОШИБКА car.disconnect: " + e);
            }
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
        Double celsius = celsiusToPublish(rawValue);
        if (celsius == null) {
            Integer raw = parseRaw(rawValue);
            log(raw == null
                    ? "температура проигнорирована: неожиданный тип значения (" + rawValue + ")"
                    : "температура проигнорирована: sentinel raw=" + raw + " (нет данных с датчика)");
            return;
        }
        final int raw = parseRaw(rawValue);
        final double value = celsius;
        mainHandler.post(() -> listener.onCabinTemperature(value, raw));
    }

    /**
     * Единственное место, где решается, показывать ли температуру.
     * null означает «не публиковать»: значение не разобралось или пришёл
     * sentinel raw < 0. Вынесено отдельно, чтобы это решение проверялось
     * тестом, — на эмуляторе путь недостижим, на голове идёт вслепую.
     */
    static Double celsiusToPublish(Object rawValue) {
        Integer raw = parseRaw(rawValue);
        if (raw == null || raw < 0) {
            return null;
        }
        return (raw - 84) / 2.0;
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
