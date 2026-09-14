package com.wt.airconditioner;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Foreground-сервис: владеет соединением с Car и выключает подогрев при
 * выключении зажигания.
 *
 * Соединение живёт здесь, а не в Activity, потому что подогрев обязан работать
 * со свёрнутым приложением: на Android 13+ без foreground-сервиса процесс
 * убивают, и автоматика молча перестаёт срабатывать.
 */
public class SeatHeatService extends Service implements CarHvacProbe.Listener {

    private static final String CHANNEL_ID = "seat_heat";
    private static final int NOTIFICATION_ID = 888;

    /** Слушатель UI; сервис работает и без него. */
    public interface UiListener {
        void onLogLine(String line);

        void onHvacReady(boolean ready);

        /** Сырое показание сюда не идёт: оно нужно только логу, внутри сервиса. */
        void onCabinTemperature(double celsius);

        /** Уровень изменился — не важно, вручную, каскадом или выключением. */
        void onSeatLevel(Seat seat, int level);
    }

    public class LocalBinder extends Binder {
        public SeatHeatService getService() {
            return SeatHeatService.this;
        }
    }

    /**
     * Единственная точка запуска: сервис поднимают три независимых пути —
     * экран, служба доступности и загрузка головы.
     */
    static Intent intentFor(Context context) {
        return new Intent(context, SeatHeatService.class);
    }

    static void start(Context context) {
        Intent intent = intentFor(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    private final IBinder binder = new LocalBinder();
    private final LogBuffer logs = new LogBuffer();

    private CarHvacProbe probe;
    private AutoHeatEngine autoHeat;
    private HeatSettings settings;
    private PresetStore presets;
    private UiListener uiListener;

    /** null — готовность ещё не известна; реплеить такое в UI нельзя. */
    private Boolean hvacReady;
    private Double lastCelsius;
    private PendingIntent contentIntent;

    /** Подтверждённое автомобилем состояние сидений — для экрана и реплея. */
    private final Map<Seat, Integer> levels = new EnumMap<>(Seat.class);

    /** Решения о зажигании: была ли поездка и выключены ли сиденья. */
    private final IgnitionSession session = new IgnitionSession();

    /**
     * Шаг ряда калибровки. Постоянная времени подушки — около двух минут, так
     * что минутные отсчёты уже размывают разгон, а чаще нет смысла: датчик
     * воздуха всё равно обновляется медленнее.
     */
    private static final long SAMPLE_INTERVAL_MS = 30_000L;

    /**
     * Сколько ряд пишется после выключения зажигания. Первые минуты голова ещё
     * отдаёт салон, дальше — только 255 всю стоянку. Опрос в это время пользы не
     * несёт, а в ночь 12→13.09 голова восемь часов не спала, и без опроса на
     * стоянке вопрос «не мы ли её держим» снимается сам.
     */
    static final long PARKED_SAMPLE_MS = 5 * 60_000L;

    private final Handler sampler = new Handler(Looper.getMainLooper());
    private CalibrationLog calibrationLog;
    private boolean sampling;

    /**
     * elapsedRealtime первого «не ON» подряд; null — зажигание ON или ещё не
     * известно. Неизвестное стоянкой не считается: без подписки на зажигание
     * ряд иначе встал бы посреди поездки.
     */
    private Long parkedSinceMs;

    /** Последнее известное зажигание — в ряд; решения принимает IgnitionSession. */
    private Boolean ignitionOn;

    /** elapsedRealtime последнего достоверного салона — для возраста входа модели. */
    private Long cabinReceivedAtMs;

    /**
     * Тепловая модель в фоне: только лог и ряд, в машину решения не уходят.
     * Снимок между перезапусками — в отдельном файле, не в настройках.
     */
    private ModelShadow modelShadow;
    private static final String MODEL_FILE = "autoheat_model";

    /** Последний записанный в лог raw: события приходят чаще, чем меняется значение. */
    private Integer loggedCabinRaw;

    private boolean calibrationFailureLogged;

    /**
     * Снимок по таймеру. Читаем сами, а не ждём события HVAC: в неподвижном
     * салоне их может не быть часами, а ряд нужен равномерный.
     */
    private final Runnable sampleTick = new Runnable() {
        @Override
        public void run() {
            probe.sample();
            updateModel();
            recordSample("tick");
            if (!keepsSampling(parkedSinceMs, SystemClock.elapsedRealtime())) {
                sampling = false;
                onLog("стоянка: ряд калибровки на паузе до зажигания ON");
                return;
            }
            sampler.postDelayed(this, SAMPLE_INTERVAL_MS);
        }
    };

    /**
     * Время стоянки — по elapsedRealtime, а не по uptime: сон головы в него
     * входит, и первый тик после ночи останавливает ряд, а не пишет ещё пять
     * минут бодрствования. Отдельно — ради теста.
     */
    static boolean keepsSampling(Long parkedSinceMs, long nowMs) {
        return parkedSinceMs == null || nowMs - parkedSinceMs < PARKED_SAMPLE_MS;
    }

    /**
     * Допустимый возраст воздуха с момента получения приложением, раздел 2
     * спецификации модели. При опросе раз в 30 секунд превышение значит
     * несколько подряд неудачных чтений или sentinel, а не неподвижный салон.
     */
    static final long MAX_AIR_AGE_MS = 120_000L;

    /**
     * Значение, если оно достоверно по возрасту, иначе null. Внутреннюю
     * устарелость головы отсюда не видно — от неё защищает пересчёт при смене
     * значения в SeatObserver.
     */
    static Double reliable(Double value, Long receivedAtMs, long nowMs) {
        if (value == null || receivedAtMs == null) {
            return null;
        }
        long age = nowMs - receivedAtMs;
        return age >= 0 && age <= MAX_AIR_AGE_MS ? value : null;
    }

    private void startSampling() {
        if (calibrationLog == null || sampling) {
            return;
        }
        sampling = true;
        sampler.postDelayed(sampleTick, SAMPLE_INTERVAL_MS);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification("Подключение к автомобилю…"));
        onLog("сервис запущен");
        settings = new HeatSettings(this);
        presets = new PresetStore(this);
        autoHeat = new AutoHeatEngine(new HandlerScheduler(), this::onLog);
        modelShadow = new ModelShadow(modelStore(), this::onLog);
        probe = new CarHvacProbe(this, this);
        startCalibrationLog();
    }

    /**
     * Ряд пишется во внутреннюю память, а не на флешку: он должен вестись
     * всегда, а не только когда носитель воткнут. На флешку его выгружает
     * кнопка «Сохранить» — там же, где диагностический лог.
     */
    private void startCalibrationLog() {
        File directory = getExternalFilesDir(null);
        if (directory == null) {
            onLog("ВНИМАНИЕ: ряд калибровки не ведётся — внешнее хранилище недоступно");
            return;
        }
        calibrationLog = new CalibrationLog(directory);
        onLog("ряд калибровки: " + calibrationLog.file().getName());
        startSampling();
    }

    private ModelShadow.Store modelStore() {
        final SharedPreferences preferences = getSharedPreferences(MODEL_FILE, MODE_PRIVATE);
        return new ModelShadow.Store() {
            @Override
            public String load(Seat seat) {
                return preferences.getString("snapshot_" + seat.name(), null);
            }

            @Override
            public void save(Seat seat, String snapshot) {
                preferences.edit().putString("snapshot_" + seat.name(), snapshot).apply();
            }
        };
    }

    /**
     * Фоновая модель — на тике ряда и на событиях уровня и зажигания, до записи
     * строки: строка должна показать оценку уже с учётом события. Сбой модели
     * остаётся внутри ModelShadow.
     */
    private void updateModel() {
        long now = SystemClock.elapsedRealtime();
        Double outside = reliable(CarHvacProbe.outsideCelsius(probe.lastOutsideRaw()),
                probe.lastOutsideAtMs(), now);
        modelShadow.update(now, System.currentTimeMillis(), levels,
                Boolean.TRUE.equals(ignitionOn), reliable(lastCelsius, cabinReceivedAtMs, now),
                outside);
    }

    /**
     * Строка ряда. Значения берём из пробника, а не из полей экрана: там уже
     * пересчитанное, а для подгонки модели нужно сырьё.
     */
    private void recordSample(String reason) {
        if (calibrationLog == null) {
            return;
        }
        CalibrationLog.Sample sample = new CalibrationLog.Sample();
        sample.reason = reason;
        sample.cabinRaw = probe.lastCabinRaw();
        /**
         * Из того же raw, а не из lastCelsius: пересчёт приходит в сервис через
         * post и отстаёт от чтения, а при sentinel не приходит вовсе — строка
         * связала бы новый raw со старой температурой.
         */
        sample.cabinCelsius = CarHvacProbe.celsiusToPublish(sample.cabinRaw);
        sample.outsideRaw = probe.lastOutsideRaw();
        sample.coolantFloats = probe.lastCoolantFloats();
        sample.coolantInts = probe.lastCoolantInts();
        sample.driverLevel = levels.get(Seat.DRIVER);
        sample.passengerLevel = levels.get(Seat.PASSENGER);
        sample.ignitionOn = ignitionOn;
        sample.driverModel = modelShadow.model(Seat.DRIVER);
        sample.passengerModel = modelShadow.model(Seat.PASSENGER);
        if (!calibrationLog.append(sample) && !calibrationFailureLogged) {
            calibrationFailureLogged = true;
            onLog("ВНИМАНИЕ: ряд калибровки не пишется — " + calibrationLog.file());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        /**
         * Пересоздаём сервис, если система его убила: подогрев должен пережить
         * нехватку памяти.
         */
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        /**
         * Единственный метод, который может застать поля незаполненными:
         * остальные пути идут после успешного onCreate и проверок не требуют.
         */
        sampler.removeCallbacks(sampleTick);
        if (autoHeat != null) {
            autoHeat.stopAll();
        }
        if (probe != null) {
            probe.disconnect();
        }
        super.onDestroy();
    }

    /**
     * --- API для Activity ---
     */

    /**
     * Подключает экран и отдаёт ему накопленное состояние. Реплей и подписка на
     * лог разведены: состояние живёт в главном потоке, откуда и приходит этот
     * вызов, а лог пополняют колбэки Car — он идёт под замком LogBuffer.
     */
    public List<String> setUiListener(UiListener listener) {
        this.uiListener = listener;
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
            listener.onSeatLevel(seat, levelOf(seat));
        }
        return logs.subscribe(listener::onLogLine);
    }

    public boolean setSeatHeat(Seat seat, int level) {
        if (!probe.setSeatHeat(seat, level)) {
            /**
             * levels держит только подтверждённое: иначе UI и engine ушли бы
             * вперёд после потерянной записи.
             */
            return false;
        }
        if (level > 0) {
            session.seatsHeating();
        }
        levels.put(seat, level);
        notifySeatLevel(seat, level);
        updateModel();
        recordSample("level");
        return true;
    }

    /** Уровень запоминается в настройках: после перезапуска экран покажет его. */
    public boolean setManualLevel(Seat seat, int level) {
        /**
         * Сначала запись в автомобиль: запомнить непринятую команду значило бы
         * обещать тепло, которого нет.
         */
        if (!setSeatHeat(seat, level)) {
            return false;
        }
        settings.setManualLevel(seat, level);
        return true;
    }

    /**
     * Снять пресет с сиденья. Сначала каскад, потом нагрев: в обратном порядке
     * ближайший шаг расписания включил бы подогрев обратно.
     *
     * false — режим уже ручной, но выключение автомобиль не принял: сиденье
     * осталось тёплым, а список пресетов с этого момента показывает «запустить».
     * Молчать об этом нельзя — человек нажал паузу и ушёл бы уверенным, что
     * подогрев снят.
     */
    public boolean stopPreset(Seat seat) {
        setMode(seat, HeatMode.MANUAL);
        if (setManualLevel(seat, 0)) {
            return true;
        }
        onLog("ВНИМАНИЕ: " + seat.title + " не выключен — пресет снят, тепло могло остаться");
        return false;
    }

    /** Очищает источник лога, а не только его текущее представление в Activity. */
    public void clearLogs() {
        logs.clear();
    }

    /**
     * Лучшая известная оценка: подтверждённое значение этой сессии, иначе —
     * прошлой. Точного ответа не существует, свойство подогрева может быть
     * недоступно на чтение.
     *
     * Следствие: после реконнекта с неудавшимся чтением остаётся значение до
     * разрыва. Это не протухшие данные — подогрев висит на HVAC автомобиля и
     * переживает перезапуск CarService.
     */
    public int levelOf(Seat seat) {
        Integer level = levels.get(seat);
        return level == null ? settings.manualLevel(seat) : level;
    }

    public HeatMode modeOf(Seat seat) {
        return settings.mode(seat);
    }

    /**
     * «Вручную» останавливает каскад, но уровень не трогает: человек мог только
     * что его выставить. «Авто» стартует сразу, если температура известна.
     */
    public void setMode(Seat seat, HeatMode mode) {
        settings.setMode(seat, mode);
        onLog("режим " + seat.title + ": " + mode.title);
        if (mode == HeatMode.AUTO) {
            startCascade(seat);
        } else {
            autoHeat.stop(seat);
        }
    }

    public void readCabinTemperature() {
        probe.readCabinTemperature();
    }

    /**
     * Температура вместо датчика: иначе каскад проверяется только зимой и только
     * в поездке. Путь тот же, что у настоящего события, — до записи в автомобиль.
     */
    public void injectTemperature(double celsius) {
        onLog("ОТЛАДКА: подставлена температура " + TemperatureConstants.celsiusText(celsius));
        lastCelsius = celsius;
        cabinReceivedAtMs = SystemClock.elapsedRealtime();
        UiListener listener = uiListener;
        if (listener != null) {
            listener.onCabinTemperature(celsius);
        }
        autoHeat.setTemperature(celsius);
    }

    /**
     * Немедленно: человек ждёт тепла сейчас, а не со следующего зажигания.
     * Порог проверяет сам движок — в тёплом салоне каскад не начнётся.
     */
    public void applyPreset(Preset preset) {
        onLog("пресет «" + preset.name + "» → " + preset.seat.title);
        settings.setMode(preset.seat, HeatMode.PRESETS);
        settings.setActivePreset(preset.seat, preset.encode());
        autoHeat.start(preset.seat, level -> setSeatHeat(preset.seat, level), preset.settings);
    }

    /**
     * Пресет отредактировали или удалили (newEncoded == null). Указатель
     * «последний пресет сиденья» хранится строкой самого пресета: без этого он
     * показывал бы на исчезнувшее расписание, и по зажиганию ON сиденье молча
     * осталось бы без подогрева.
     *
     * Идущий каскад не прерывается — гасить тепло под человеком из-за правки
     * записи в списке хуже того, что этим лечится.
     */
    public void onPresetChanged(String oldEncoded, String newEncoded) {
        for (Seat seat : Seat.values()) {
            if (oldEncoded.equals(settings.activePreset(seat))) {
                settings.setActivePreset(seat, newEncoded);
                onLog(newEncoded == null
                        ? "пресет удалён → " + seat.title + " ждёт нового выбора"
                        : "пресет изменён → " + seat.title + " продолжит с новым расписанием");
            }
        }
    }

    /**
     * Пресет, который ведёт сиденье сейчас, или null. Именно пара «режим +
     * активный пресет», а не наличие таймера: дошедший до нуля каскад пресет с
     * сиденья не снимает — по следующему ON он запустится снова.
     *
     * Ответ про сиденье, а не про пресет: у всех записей одного сиденья он общий.
     */
    public String runningPreset(Seat seat) {
        return settings.mode(seat) == HeatMode.PRESETS ? settings.activePreset(seat) : null;
    }

    /**
     * Повторяет последний пресет сиденья. false — повторять нечего: пресет ещё
     * не выбирали или его удалили, и тогда человека нужно отправить выбирать.
     */
    public boolean applyActivePreset(Seat seat) {
        Preset preset = presets.find(settings.activePreset(seat));
        if (preset == null) {
            return false;
        }
        applyPreset(preset);
        return true;
    }

    /**
     * Единственный способ проверить расписание на голове, не дожидаясь зимы:
     * обычный триггер (зажигание ON) бывает раз в поездку и только в холод.
     */
    public void startAutoHeatNow() {
        onLog("--- ручной запуск каскада ---");
        startAutoHeat();
    }

    /**
     * Рвёт связь с Car и поднимает заново: перезапуск самого CarService на голове
     * недоступен, а так проходится весь наш код — teardown менеджеров, повторный
     * connect, переоформление подписок, чтение стартового зажигания.
     */
    public void restartCarConnection() {
        onLog("--- ручное переподключение к Car ---");
        probe.disconnect();
        /**
         * Готовность снова неизвестна: реплей старого значения соврал бы
         * экрану, который откроют после переподключения.
         */
        hvacReady = null;
        probe = new CarHvacProbe(this, this);
    }

    /**
     * --- CarHvacProbe.Listener ---
     */

    @Override
    public void onLog(String message) {
        logs.append(message);
    }

    @Override
    public void onHvacReady(boolean ready) {
        hvacReady = ready;
        updateNotification(ready ? "Подогрев сидений активен" : "Нет связи с автомобилем");
        if (ready) {
            syncSeatLevels();
        }
        UiListener listener = uiListener;
        if (listener != null) {
            listener.onHvacReady(ready);
        }
    }

    /**
     * Спрашивает у автомобиля, что стоит на сиденьях: подогрев запитан от ГУ и
     * гаснет вместе с ним, поэтому после перезагрузки головы экран показывал бы
     * тепло, которого нет.
     *
     * Читаем, а не переигрываем сохранённое обратно в HVAC: включать подогрев в
     * пустой машине нельзя. Если чтение не вышло, прежняя оценка остаётся
     * намеренно — см. levelOf().
     */
    private void syncSeatLevels() {
        for (Seat seat : Seat.values()) {
            Integer level = probe.readSeatHeat(seat);
            if (level == null) {
                onLog("уровень " + seat.title + " не прочитан — остаётся прежняя оценка: "
                        + levelOf(seat));
                continue;
            }
            levels.put(seat, level);
            if (level > 0) {
                session.seatsHeating();
            }
            notifySeatLevel(seat, level);
        }
        updateModel();
    }

    @Override
    public void onCabinTemperature(double celsius, int raw) {
        lastCelsius = celsius;
        cabinReceivedAtMs = SystemClock.elapsedRealtime();
        /**
         * Только при изменении: события идут и от подписки, и от снимков раз в
         * полминуты, а буфер лога — 500 строк. Ряд калибровки пишет каждое
         * значение независимо, там пропусков не будет.
         */
        if (loggedCabinRaw == null || loggedCabinRaw != raw) {
            loggedCabinRaw = raw;
            onLog("температура: raw=" + raw + " → " + TemperatureConstants.celsiusText(celsius));
        }
        autoHeat.setTemperature(celsius);
        UiListener listener = uiListener;
        if (listener != null) {
            listener.onCabinTemperature(celsius);
        }
    }

    @Override
    public void onWakeUp() {
        onLog("голова проснулась → новая сессия");
        session.onWakeUp();
        autoHeat.stopAll();
    }

    @Override
    public void onIgnition(boolean on) {
        ignitionOn = on;
        /**
         * Отсчёт стоянки — от первого «не ON»: голова шлёт их пачкой (ACC,
         * затем OFF), и каждое следующее не должно продлевать запись.
         */
        if (on) {
            parkedSinceMs = null;
            startSampling();
        } else if (parkedSinceMs == null) {
            parkedSinceMs = SystemClock.elapsedRealtime();
        }
        /**
         * До обработки: строка фиксирует момент события с уровнями, какими они
         * были. Их изменение запишется своими строками из setSeatHeat.
         */
        updateModel();
        recordSample("ignition");
        switch (session.onIgnition(on)) {
            case START_HEAT:
                onLog("зажигание ON — с этого момента выключение по зажиганию активно");
                startAutoHeat();
                break;
            case SHUTDOWN:
                /**
                 * Одной попытки достаточно: проверка на голове показала, что при
                 * выключении зажигания ГУ гаснет мгновенно (с открытой дверью —
                 * сразу), унося с собой наш процесс, и тем же выключением
                 * обесточивается сам подогрев. Ретраи здесь просто не успели бы
                 * выполниться, а если бы успели — гасить было бы уже нечего.
                 */
                onLog("зажигание выключено → выключаю оба сиденья");
                autoHeat.stopAll();
                shutdownSeats();
                break;
            default:
                break;
        }
    }

    /**
     * По зажиганию ON, а не по старту сервиса: ГУ поднимается от открытия двери,
     * но без нагрузки на сиденье нагреватель не включается (NATIVE_MIGRATION.md,
     * «Принятое допущение»). ON — первый момент, когда человек точно сидит.
     */
    private void startAutoHeat() {
        /**
         * Освежаем температуру, а не добываем её: последнее известное значение
         * движок помнит и сам, поэтому неудачное чтение (HVAC ещё не готов)
         * больше не подвешивает каскад в «жду температуру».
         */
        probe.readCabinTemperature();
        for (Seat seat : Seat.values()) {
            HeatMode mode = settings.mode(seat);
            if (mode == HeatMode.AUTO) {
                startCascade(seat);
            } else if (mode == HeatMode.PRESETS) {
                /**
                 * Сиденье осталось в режиме пресета с прошлой поездки — значит
                 * его и ждут, а не ручное управление.
                 */
                applyActivePreset(seat);
            }
        }
    }

    private void startCascade(Seat seat) {
        onLog("автоподогрев для " + seat.title + " → жду температуру");
        autoHeat.start(seat, level -> setSeatHeat(seat, level));
    }

    /**
     * Обе записи учитываются независимо: если прошла только одна, состояние не
     * подтверждено и следующее событие зажигания попробует ещё раз.
     */
    private void shutdownSeats() {
        boolean confirmed = true;
        for (Seat seat : Seat.values()) {
            /**
             * Именно `&`, а не `&&`: выключить нужно оба сиденья, даже если
             * запись в первое не прошла.
             */
            confirmed &= setSeatHeat(seat, 0);
        }
        session.shutdownResult(confirmed);
        if (!confirmed) {
            onLog("ВНИМАНИЕ: выключение сидений не подтверждено");
        }
    }

    private void notifySeatLevel(Seat seat, int level) {
        UiListener listener = uiListener;
        if (listener != null) {
            listener.onSeatLevel(seat, level);
        }
    }

    /**
     * --- уведомление ---
     */

    /** Канал и PendingIntent живут сколько сервис — заводятся один раз. */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Подогрев сидений", NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String text) {
        if (contentIntent == null) {
            contentIntent = PendingIntent.getActivity(
                    this, 0, new Intent(this, MainActivity.class),
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                            ? PendingIntent.FLAG_IMMUTABLE
                            : 0);
        }
        PendingIntent content = contentIntent;

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
