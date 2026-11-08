package com.wt.airconditioner;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;

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
public class SeatHeatService extends Service implements CarHvacProbe.Listener, Diagnostics, SeatControl {

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
    /** Экран, лог и накопленное для реплея состояние. */
    private final ScreenBridge screen = new ScreenBridge(this::levelOf);
    private HeatNotification notification;

    private CarHvacProbe probe;
    /** Авто по модели и пользовательские расписания пресетов. */
    private ModelHeatDriver driver;
    private HeatSettings settings;
    private PresetStore presets;

    /** Подтверждённое автомобилем состояние сидений — для экрана и реплея. */
    private final Map<Seat, Integer> levels = new EnumMap<>(Seat.class);

    /** Решения о зажигании: была ли поездка и выключены ли сиденья. */
    private final IgnitionSession session = new IgnitionSession();

    /** Ряд калибровки со своим тиком; он же держит порядок «модель до строки». */
    private CalibrationSampler sampler;

    /** Последнее известное зажигание — в ряд; решения принимает IgnitionSession. */
    private Boolean ignitionOn;

    /**
     * Тепловая модель: оценки обоих сидений, решения для авто, лог и ряд.
     * Снимок между перезапусками — в отдельном файле, не в настройках.
     */
    private ModelShadow modelShadow;
    private static final String MODEL_FILE = "autoheat_model";
    private Double injectedCabin;
    private Long injectedCabinAtMs;

    @Override
    public void onCreate() {
        super.onCreate();
        notification = new HeatNotification(this);
        startForeground(HeatNotification.ID, notification.build("Подключение к автомобилю…"));
        log("сервис запущен");
        settings = new HeatSettings(this);
        presets = new PresetStore(this);
        modelShadow = new ModelShadow(modelStore(), this);
        driver = new ModelHeatDriver(new HandlerScheduler(), modelShadow, this::updateModel, this);
        probe = new CarHvacProbe(this, this);
        sampler = new CalibrationSampler(getExternalFilesDir(null), sampleSource, this);
        /** Тик ряда освежает воздух; авто дополнительно проверяет модель раз в 15 с. */
        sampler.start();
    }

    private ModelShadow.Store modelStore() {
        final SharedPreferences preferences = getSharedPreferences(MODEL_FILE, MODE_PRIVATE);
        return new ModelShadow.Store() {
            @Override
            public String load(Seat seat) {
                return preferences.getString("snapshot_" + seat.name(), null);
            }

            @Override
            public void save(Map<Seat, String> snapshots) {
                SharedPreferences.Editor editor = preferences.edit();
                for (Map.Entry<Seat, String> entry : snapshots.entrySet()) {
                    editor.putString("snapshot_" + entry.getKey().name(), entry.getValue());
                }
                editor.apply();
            }
        };
    }

    /**
     * Что видит ряд калибровки. Значения берём из пробника, а не из полей
     * экрана: там уже пересчитанное, а для подгонки модели нужно сырьё.
     */
    private final CalibrationSampler.Source sampleSource = new CalibrationSampler.Source() {
        @Override
        public void refresh() {
            /**
             * Читаем сами, а не ждём события HVAC: в неподвижном салоне их
             * может не быть часами, а ряд нужен равномерный.
             */
            probe.sample();
        }

        @Override
        public void updateModel() {
            SeatHeatService.this.updateModel();
        }

        @Override
        public CalibrationLog.Sample sample(String reason) {
            CalibrationLog.Sample sample = new CalibrationLog.Sample();
            sample.reason = reason;
            sample.cabinRaw = probe.lastCabinRaw();
            /**
             * Из того же raw, а не из lastCelsius: пересчёт приходит в сервис
             * через post и отстаёт от чтения, а при sentinel не приходит вовсе
             * — строка связала бы новый raw со старой температурой.
             */
            sample.cabinCelsius = CarHvacProbe.celsiusToPublish(sample.cabinRaw);
            sample.outsideRaw = probe.lastOutsideRaw();
            sample.coolantFloats = probe.lastCoolantFloats();
            sample.coolantInts = probe.lastCoolantInts();
            sample.driverLevel = levels.get(Seat.DRIVER);
            sample.passengerLevel = levels.get(Seat.PASSENGER);
            sample.ignitionOn = ignitionOn;
            sample.driverModel = model(modelShadow.estimate(Seat.DRIVER));
            sample.passengerModel = model(modelShadow.estimate(Seat.PASSENGER));
            return sample;
        }
    };

    /**
     * Оценка модели в колонки ряда. Перекладывает сервис: расчёт про формат
     * файла знать не должен.
     */
    private static CalibrationLog.Model model(ModelShadow.Estimate estimate) {
        if (estimate == null) {
            return null;
        }
        CalibrationLog.Model model = new CalibrationLog.Model();
        model.baseLow = estimate.base.low;
        model.baseHigh = estimate.base.high;
        model.bankLow = estimate.bank.low;
        model.bankHigh = estimate.bank.high;
        model.level = estimate.level;
        model.origin = estimate.origin;
        return model;
    }

    /**
     * Модель видит подтверждённые уровни. Только после расчёта драйвер может
     * отправить новую команду через обычный setSeatHeat.
     */
    private void updateModel() {
        long nowMs = SystemClock.elapsedRealtime();
        Air air = new Air(injectedCabin == null ? probe.lastCabinCelsius() : injectedCabin,
                injectedCabin == null ? probe.lastCabinAtMs() : injectedCabinAtMs,
                CarHvacProbe.outsideCelsius(probe.lastOutsideRaw()), probe.lastOutsideAtMs());
        boolean on = Boolean.TRUE.equals(ignitionOn);
        modelShadow.update(nowMs, System.currentTimeMillis(), levels, on, air);
        driver.apply(nowMs, levels, on, air);
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
        if (sampler != null) {
            sampler.stop();
        }
        if (driver != null) {
            driver.stopAll();
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
        return screen.attach(listener);
    }

    private boolean setSeatHeat(Seat seat, int level) {
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
        screen.seatLevel(seat, level);
        sampler.record("level");
        return true;
    }

    /** Уровень запоминается в настройках: после перезапуска экран покажет его. */
    @Override
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
        log("ВНИМАНИЕ: " + seat.title + " не выключен — пресет снят, тепло могло остаться");
        return false;
    }

    /** Очищает источник лога, а не только его текущее представление в Activity. */
    @Override
    public void clearLogs() {
        screen.clearLogs();
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
    @Override
    public int levelOf(Seat seat) {
        Integer level = levels.get(seat);
        return level == null ? settings.manualLevel(seat) : level;
    }

    @Override
    public HeatMode modeOf(Seat seat) {
        return settings.mode(seat);
    }

    /**
     * «Вручную» останавливает каскад, но уровень не трогает: человек мог только
     * что его выставить. «Авто» стартует сразу, если температура известна.
     */
    @Override
    public void setMode(Seat seat, HeatMode mode) {
        settings.setMode(seat, mode);
        log("режим " + seat.title + ": " + mode.title);
        if (mode == HeatMode.AUTO) {
            startAuto(seat);
        } else {
            /**
             * Пресет по переключению режима не запускается: человек выбирает
             * его сам следующим действием. Автоматически его повторяет только
             * зажигание ON — см. resume().
             */
            driver.stop(seat);
        }
    }

    private void startAuto(Seat seat) {
        log("автоподогрев для " + seat.title + " → тепловая модель");
        driver.start(seat, level -> setSeatHeat(seat, level));
    }

    /**
     * Вернуть сиденье под управление после зажигания ON — по режиму, в котором
     * его оставили. Пресет здесь повторяется: сиденье осталось в этом режиме с
     * прошлой поездки, значит его и ждут.
     */
    private void resume(Seat seat) {
        HeatMode mode = settings.mode(seat);
        if (mode == HeatMode.AUTO) {
            startAuto(seat);
        } else if (mode == HeatMode.PRESETS) {
            applyActivePreset(seat);
        }
    }

    @Override
    public void readCabinTemperature() {
        probe.readCabinTemperature();
    }

    /**
     * Температура вместо датчика: иначе каскад проверяется только зимой и только
     * в поездке. Путь тот же, что у настоящего события, — до записи в автомобиль.
     */
    @Override
    public void injectTemperature(double celsius) {
        log("ОТЛАДКА: подставлена температура " + Temperature.celsiusText(celsius));
        injectedCabin = celsius;
        injectedCabinAtMs = SystemClock.elapsedRealtime();
        screen.showTemperature(celsius);
        driver.setTemperature(celsius);
    }

    /**
     * Немедленно: человек ждёт тепла сейчас, а не со следующего зажигания.
     * Порог проверяет сам движок — в тёплом салоне каскад не начнётся.
     */
    public void applyPreset(Preset preset) {
        log("пресет «" + preset.name + "» → " + preset.seat.title);
        settings.setMode(preset.seat, HeatMode.PRESETS);
        settings.setActivePreset(preset.seat, preset.encode());
        driver.start(preset.seat, level -> setSeatHeat(preset.seat, level), preset.settings);
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
                log(newEncoded == null
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
    @Override
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
    @Override
    public void startAutoHeatNow() {
        log("--- ручной запуск автоподогрева ---");
        startAutoHeat();
    }

    /**
     * Рвёт связь с Car и поднимает заново: перезапуск самого CarService на голове
     * недоступен, а так проходится весь наш код — teardown менеджеров, повторный
     * connect, переоформление подписок, чтение стартового зажигания.
     */
    @Override
    public void restartCarConnection() {
        log("--- ручное переподключение к Car ---");
        probe.disconnect();
        /**
         * Готовность снова неизвестна: реплей старого значения соврал бы
         * экрану, который откроют после переподключения.
         */
        screen.forgetHvacReady();
        probe = new CarHvacProbe(this, this);
    }

    /**
     * --- CarHvacProbe.Listener ---
     */

    /**
     * Имя от CarHvacProbe.Listener; сама запись — в log(). Два имени у одной
     * операции только потому, что колбэк пробника называется так.
     */
    @Override
    public void onLog(String message) {
        log(message);
    }

    @Override
    public void log(String message) {
        screen.log(message);
    }

    @Override
    public void onHvacReady(boolean ready) {
        notification.update(ready ? "Подогрев сидений активен" : "Нет связи с автомобилем");
        if (ready) {
            syncSeatLevels();
        }
        /** После syncSeatLevels: экран получит готовность с уже верными уровнями. */
        screen.hvacReady(ready);
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
                log("уровень " + seat.title + " не прочитан — остаётся прежняя оценка: "
                        + levelOf(seat));
                continue;
            }
            levels.put(seat, level);
            if (level > 0) {
                session.seatsHeating();
            }
            screen.seatLevel(seat, level);
        }
        updateModel();
    }

    @Override
    public void onCabinTemperature(double celsius, int raw) {
        injectedCabin = null;
        injectedCabinAtMs = null;
        screen.cabinTemperature(celsius, raw);
        driver.setTemperature(celsius);
    }

    @Override
    public void onWakeUp() {
        log("голова проснулась → новая сессия");
        session.onWakeUp();
        driver.stopAll();
    }

    @Override
    public void onIgnition(boolean on) {
        ignitionOn = on;
        sampler.onIgnition(on);
        /**
         * До обработки: строка фиксирует момент события с уровнями, какими они
         * были. Их изменение запишется своими строками из setSeatHeat.
         */
        sampler.record("ignition");
        switch (session.onIgnition(on)) {
            case START_HEAT:
                log("зажигание ON — с этого момента выключение по зажиганию активно");
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
                log("зажигание выключено → выключаю оба сиденья");
                driver.stopAll();
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
            resume(seat);
        }
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
            log("ВНИМАНИЕ: выключение сидений не подтверждено");
        }
    }

}
