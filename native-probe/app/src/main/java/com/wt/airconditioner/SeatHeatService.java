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

    /** Слушатель UI; сервис работает и без него. */
    public interface UiListener {
        void onLogLine(String line);

        void onHvacReady(boolean ready);

        /** raw может быть null для температуры, введённой в debug-инжекторе. */
        void onCabinTemperature(double celsius, Integer raw);

        /** Уровень изменился — не важно, вручную, каскадом или выключением. */
        void onSeatLevel(Seat seat, int level);
    }

    public class LocalBinder extends Binder {
        public SeatHeatService getService() {
            return SeatHeatService.this;
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
    private Integer lastRaw;

    /** Что сейчас выставлено на каждом сиденье — для экрана и для реплея. */
    private final java.util.Map<Seat, Integer> levels = new java.util.EnumMap<>(Seat.class);

    /** Была ли поездка и выключены ли сиденья — решения о зажигании живут там. */
    private final IgnitionSession session = new IgnitionSession();

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(NOTIFICATION_ID, buildNotification("Подключение к автомобилю…"));
        onLog("сервис запущен");
        settings = new HeatSettings(this);
        presets = new PresetStore(this);
        autoHeat = new AutoHeatEngine(new HandlerScheduler(), this::onLog);
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
        if (autoHeat != null) {
            autoHeat.stopAll();
        }
        if (probe != null) {
            probe.disconnect();
        }
        super.onDestroy();
    }

    // --- API для Activity ---

    /**
     * Подключает экран и отдаёт ему накопленное состояние. Реплей состояния и
     * подписка на лог разведены: состояние живёт только в главном потоке,
     * откуда и приходит этот вызов, а лог пополняется колбэками Car и потому
     * подписывается под собственным замком LogBuffer.
     */
    public List<String> setUiListener(UiListener listener) {
        this.uiListener = listener;
        if (listener == null) {
            logs.unsubscribe();
            return new ArrayList<>();
        }
        // hvacReady == null — ответа от Car ещё нет. Реплей false выглядел бы
        // на экране как вердикт «HVAC недоступен», хотя подключение просто не
        // завершилось: для пробника это ложный диагноз.
        if (hvacReady != null) {
            listener.onHvacReady(hvacReady);
        }
        if (lastCelsius != null) {
            listener.onCabinTemperature(lastCelsius, lastRaw);
        }
        for (Seat seat : Seat.values()) {
            listener.onSeatLevel(seat, levelOf(seat));
        }
        return logs.subscribe(listener::onLogLine);
    }

    public boolean setSeatHeat(Seat seat, int level) {
        if (probe == null || !probe.setSeatHeat(seat == Seat.DRIVER, level)) {
            // `levels` содержит только подтверждённое состояние автомобиля.
            // Иначе UI и engine могли бы перейти вперёд после потерянной записи.
            return false;
        }
        if (level > 0) {
            session.seatsHeating();
        }
        levels.put(seat, level);
        notifySeatLevel(seat, level);
        return true;
    }

    /**
     * Ручная установка уровня: запоминается в настройках, чтобы экран после
     * перезапуска показывал то же, что греет в машине.
     */
    public void setManualLevel(Seat seat, int level) {
        // Сначала запись в автомобиль: сохранённый уровень — это то, что экран
        // покажет после перезапуска вместо забытого levels. Запомнить
        // непринятую команду значило бы обещать тепло, которого нет.
        if (setSeatHeat(seat, level)) {
            settings.setManualLevel(seat, level);
        }
    }

    /** Очищает источник лога, а не только его текущее представление в Activity. */
    public void clearLogs() {
        logs.clear();
    }

    /**
     * Лучшая известная оценка уровня на сиденье, по убыванию достоверности:
     * подтверждённое автомобилем значение этой сессии, иначе — подтверждённое
     * в прошлой (settings). Точного ответа не существует: свойство подогрева
     * может оказаться недоступным на чтение, и тогда единственный источник —
     * то, что автомобиль принял от нас раньше.
     *
     * Отсюда следствие, о котором стоит помнить: после реконнекта с неудавшимся
     * чтением здесь останется значение до разрыва. Это не «протухшие данные»,
     * а лучшая доступная оценка — подогрев висит на HVAC автомобиля и переживает
     * перезапуск CarService, так что уровень до разрыва вероятнее, чем ноль или
     * настройка недельной давности.
     */
    public int levelOf(Seat seat) {
        Integer level = levels.get(seat);
        return level == null ? settings.manualLevel(seat) : level;
    }

    public HeatMode modeOf(Seat seat) {
        return settings.mode(seat);
    }

    /**
     * Смена режима сиденья. «Вручную» останавливает каскад, но уровень не
     * трогает: человек мог только что его выставить. «Авто» запускает каскад
     * сразу, если температура уже известна, — ждать следующего зажигания
     * незачем, а по ON он всё равно перезапустится.
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
     * Подставляет температуру вместо датчика. Иначе каскад проверяется только
     * зимой и только в поездке: на голове летом, на эмуляторе всегда — событий
     * от датчика просто нет. Путь тот же, что у настоящего события, поэтому
     * проверяется вся цепочка до записи уровня в автомобиль.
     */
    public void injectTemperature(double celsius) {
        onLog("ОТЛАДКА: подставлена температура " + String.format(Locale.US, "%.1f", celsius)
                + " °C");
        lastCelsius = celsius;
        lastRaw = null;
        UiListener listener = uiListener;
        if (listener != null) {
            // raw = null, как и в реплее после переподписки: у подставленного
            // значения нет сырого показания. Ноль здесь означал бы −42 °C —
            // вполне правдоподобное показание датчика.
            listener.onCabinTemperature(celsius, null);
        }
        autoHeat.setTemperature(celsius);
    }

    /**
     * Запускает пресет немедленно: человек выбрал расписание руками и ждёт
     * тепла сейчас, а не со следующего зажигания. Порог пресета проверяет сам
     * движок — в тёплом салоне каскад не начнётся.
     */
    public void applyPreset(Preset preset) {
        onLog("пресет «" + preset.name + "» → " + preset.seat.title);
        settings.setMode(preset.seat, HeatMode.PRESETS);
        settings.setActivePreset(preset.seat, preset.encode());
        autoHeat.start(preset.seat, level -> setSeatHeat(preset.seat, level), preset.settings);
    }

    /**
     * Пресет отредактировали или удалили (newEncoded == null). Указатель
     * «последний пресет сиденья» хранится строкой самого пресета, поэтому без
     * этого он показывал бы на расписание, которого больше нет: по зажиганию
     * ON сиденье молча осталось бы без подогрева.
     *
     * Идущий каскад намеренно не прерывается: он всегда доходит до нуля сам, а
     * гасить тепло под человеком из-за правки записи в списке — хуже того, что
     * этим лечится.
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
     * Запуск каскада вручную — единственный способ проверить расписание на
     * голове, не дожидаясь зимы и поездки: обычный триггер, зажигание ON,
     * бывает раз в поездку и только при холодном салоне.
     */
    public void startAutoHeatNow() {
        onLog("--- ручной запуск каскада ---");
        startAutoHeat();
    }

    /**
     * Рвёт связь с Car и поднимает её заново. Нужно затем, что перезапуск
     * самого CarService на голове недоступен: настоящий onServiceDisconnected
     * так не вызвать, но весь наш код — teardown обоих менеджеров, повторный
     * connect, переоформление подписок, чтение стартового зажигания —
     * проходится целиком. Остаётся непроверенным только приход коллбэка от
     * системы, а его делает платформа.
     */
    public void restartCarConnection() {
        onLog("--- ручное переподключение к Car ---");
        if (probe != null) {
            probe.disconnect();
        }
        // Готовность снова неизвестна: реплей старого значения соврал бы
        // экрану, который откроют после переподключения.
        hvacReady = null;
        probe = new CarHvacProbe(this, this);
    }

    // --- CarHvacProbe.Listener ---

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
     * Спрашивает у автомобиля, что стоит на сиденьях. Нужно после каждого
     * подключения: сохранённый вручную уровень — это не уровень в машине.
     * Подогрев запитан от ГУ и гаснет вместе с ним, поэтому после перезагрузки
     * головы экран показывал бы тепло, которого нет.
     *
     * Именно читаем, а не переигрываем сохранённое обратно в HVAC: включать
     * подогрев в пустой машине приложение не должно — каскад начинается по
     * зажиганию ON, когда человек гарантированно сидит.
     *
     * Если прочитать не вышло, прежняя оценка остаётся намеренно — см.
     * levelOf(). Обнулить её значило бы заменить правдоподобное значение на
     * заведомо выдуманное.
     */
    private void syncSeatLevels() {
        for (Seat seat : Seat.values()) {
            Integer level = probe.readSeatHeat(seat == Seat.DRIVER);
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
        lastRaw = raw;
        onLog("температура: raw=" + raw + " → " + String.format(Locale.US, "%.1f", celsius) + " °C");
        autoHeat.setTemperature(celsius);
        UiListener listener = uiListener;
        if (listener != null) {
            listener.onCabinTemperature(celsius, raw);
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
                // Одной попытки достаточно: проверка на голове показала, что при
                // выключении зажигания ГУ гаснет мгновенно (с открытой дверью —
                // сразу), унося с собой наш процесс, и тем же выключением
                // обесточивается сам подогрев. Ретраи здесь просто не успели бы
                // выполниться, а если бы успели — гасить было бы уже нечего.
                onLog("зажигание выключено → выключаю оба сиденья");
                autoHeat.stopAll();
                shutdownSeats();
                break;
            default:
                break;
        }
    }

    /**
     * Каскад запускается по зажиганию ON, а не по старту сервиса. ГУ поднимается
     * от открытия двери, но греть в этот момент некого: без нагрузки на сиденье
     * нагреватель не включается (см. NATIVE_MIGRATION.md, «Принятое допущение»).
     * ON — первый момент, когда человек гарантированно сидит.
     */
    private void startAutoHeat() {
        // Каскаду нужна температура, а её текущее значение движок забывает при
        // каждом stopAll (пробуждение головы, выключение зажигания). Без этого
        // чтения запуск упирается в «жду температуру» и стоит до тех пор, пока
        // датчик сам не сообщит об изменении, — в холодном неподвижном салоне
        // это минуты.
        if (probe != null) {
            probe.readCabinTemperature();
        }
        for (Seat seat : Seat.values()) {
            HeatMode mode = settings.mode(seat);
            if (mode == HeatMode.AUTO) {
                startCascade(seat);
            } else if (mode == HeatMode.PRESETS) {
                // Сиденье осталось в режиме пресета с прошлой поездки — значит
                // его и ждут, а не ручное управление.
                applyActivePreset(seat);
            }
        }
    }

    private void startCascade(Seat seat) {
        onLog("автоподогрев для " + seat.title + " → жду температуру");
        autoHeat.start(seat, level -> setSeatHeat(seat, level));
    }

    /**
     * Выключение обоих сидений. Обе записи учитываются независимо: если
     * прошла только одна, состояние не считается подтверждённым и следующее
     * событие зажигания попробует ещё раз.
     */
    private void shutdownSeats() {
        boolean driver = probe.setSeatHeat(true, 0);
        boolean passenger = probe.setSeatHeat(false, 0);
        if (driver) {
            levels.put(Seat.DRIVER, 0);
            notifySeatLevel(Seat.DRIVER, 0);
        }
        if (passenger) {
            levels.put(Seat.PASSENGER, 0);
            notifySeatLevel(Seat.PASSENGER, 0);
        }
        boolean confirmed = driver && passenger;
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
