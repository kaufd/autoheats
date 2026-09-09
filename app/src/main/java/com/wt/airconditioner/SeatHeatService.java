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

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification("Подключение к автомобилю…"));
        onLog("сервис запущен");
        settings = new HeatSettings(this);
        presets = new PresetStore(this);
        autoHeat = new AutoHeatEngine(new HandlerScheduler(), this::onLog);
        probe = new CarHvacProbe(this, this);
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
    }

    @Override
    public void onCabinTemperature(double celsius, int raw) {
        lastCelsius = celsius;
        onLog("температура: raw=" + raw + " → " + TemperatureConstants.celsiusText(celsius));
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
