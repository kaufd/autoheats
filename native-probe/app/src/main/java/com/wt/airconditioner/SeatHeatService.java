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
    static final int LOG_CAPACITY = 500;

    /** Слушатель UI; сервис работает и без него. */
    public interface UiListener {
        void onLogLine(String line);

        void onHvacReady(boolean ready);

        void onCabinTemperature(double celsius, int raw);

        /** Уровень изменился — не важно, вручную, каскадом или выключением. */
        void onSeatLevel(Seat seat, int level);
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
    private AutoHeatEngine autoHeat;
    private HeatSettings settings;
    private PresetStore presets;
    private UiListener uiListener;

    /** null — готовность ещё не известна; реплеить такое в UI нельзя. */
    private Boolean hvacReady;
    private Double lastCelsius;
    private Integer lastRaw;

    /**
     * Сиденья подтверждённо выключены. Голова отдаёт выключение зажигания
     * лестницей состояний (ACC → OFF), и без этого флага каждая ступень
     * повторяла бы уже выполненное выключение, забивая лог дублями.
     *
     * На старте — true, и это не догадка: подогрев запитан от ГУ и гаснет
     * вместе с ним, так что к моменту запуска сервиса сиденья заведомо
     * выключены. Иначе сервис, перезапущенный при живом ГУ в ACC
     * (START_STICKY, обновление, краш), погасил бы подогрев, который водитель
     * включил сам и продолжает им пользоваться.
     */
    private boolean seatsOff = true;

    /** Что сейчас выставлено на каждом сиденье — для экрана и для реплея. */
    private final java.util.Map<Seat, Integer> levels = new java.util.EnumMap<>(Seat.class);

    /**
     * В этой сессии зажигание уже было ON — то есть машина ехала, и следующее
     * «не ON» означает конец поездки.
     *
     * ГУ на этой голове стартует от открытия водительской двери, задолго до
     * зажигания, и подогрев в этот момент уже физически работает (проверено на
     * машине: сиденья греют при выключенном зажигании). Без этого флага первое
     * же событие «не ON» гасило бы подогрев, включённый пока человек садится,
     * — ровно то, ради чего приложение и существует.
     */
    private boolean sawIgnitionOn;

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
        if (probe != null) {
            probe.disconnect();
        }
        super.onDestroy();
    }

    // --- API для Activity ---

    /**
     * Подключает экран и отдаёт ему накопленное состояние. Лог возвращается
     * отсюда же под тем же замком, что и его пополнение: иначе строка,
     * пришедшая между снимком и подпиской, потерялась бы.
     */
    public List<String> setUiListener(UiListener listener) {
        synchronized (logLines) {
            this.uiListener = listener;
            if (listener == null) {
                return new ArrayList<>();
            }
            List<String> snapshot = new ArrayList<>(logLines);
            // hvacReady == null — ответа от Car ещё нет. Реплей false выглядел
            // бы на экране как вердикт «HVAC недоступен», хотя подключение
            // просто не завершилось: для пробника это ложный диагноз.
            if (hvacReady != null) {
                listener.onHvacReady(hvacReady);
            }
            if (lastCelsius != null) {
                listener.onCabinTemperature(lastCelsius, lastRaw);
            }
            for (Seat seat : Seat.values()) {
                listener.onSeatLevel(seat, levelOf(seat));
            }
            return snapshot;
        }
    }

    public void setSeatHeat(Seat seat, int level) {
        if (level > 0) {
            seatsOff = false;
        }
        levels.put(seat, level);
        probe.setSeatHeat(seat == Seat.DRIVER, level);
        UiListener listener = uiListener;
        if (listener != null) {
            listener.onSeatLevel(seat, level);
        }
    }

    /**
     * Ручная установка уровня: запоминается в настройках, чтобы экран после
     * перезапуска показывал то же, что греет в машине.
     */
    public void setManualLevel(Seat seat, int level) {
        settings.setManualLevel(seat, level);
        setSeatHeat(seat, level);
    }

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
            listener.onCabinTemperature(celsius, 0);
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
        String line = timeFormat.format(new Date()) + "  " + message;
        // Пополнение буфера и выдача строки экрану — под одним замком с
        // setUiListener: иначе строка, добавленная между снимком и подпиской,
        // ушла бы в UI дважды.
        synchronized (logLines) {
            if (logLines.size() >= LOG_CAPACITY) {
                logLines.removeFirst();
            }
            logLines.addLast(line);
            if (uiListener != null) {
                uiListener.onLogLine(line);
            }
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
        autoHeat.setTemperature(celsius);
        UiListener listener = uiListener;
        if (listener != null) {
            listener.onCabinTemperature(celsius, raw);
        }
    }

    @Override
    public void onWakeUp() {
        // Короткая стоянка: голова спала, процесс жив, но в машину сели
        // заново. Состояние сессии начинается с нуля — иначе первое же «не ON»
        // после пробуждения сочли бы концом старой поездки, а автоподогрев,
        // когда он появится, не запустился бы вовсе.
        onLog("голова проснулась → новая сессия");
        sawIgnitionOn = false;
        seatsOff = true;
        autoHeat.stopAll();
    }

    @Override
    public void onIgnition(boolean on) {
        if (on) {
            if (!sawIgnitionOn) {
                onLog("зажигание ON — с этого момента выключение по зажиганию активно");
                startAutoHeat();
            }
            sawIgnitionOn = true;
            return;
        }
        if (!sawIgnitionOn) {
            // Машину ещё не заводили: ГУ подняли открытием двери, человек
            // садится. Подогрев в этот момент уже может работать — гасить его
            // здесь значит ломать то, ради чего приложение и нужно.
            return;
        }
        if (seatsOff) {
            // ACC → OFF идут одно за другим; второе выключение ничего не
            // меняет, только прячет в логе то, что важно.
            return;
        }
        // Зажигание выключено — гасим оба сиденья.
        //
        // Одной попытки достаточно: проверка на голове показала, что при
        // выключении зажигания ГУ гаснет мгновенно (с открытой дверью —
        // сразу), унося с собой наш процесс, и тем же выключением
        // обесточивается сам подогрев. Ретраи здесь просто не успели бы
        // выполниться, а если бы успели — гасить было бы уже нечего.
        onLog("зажигание выключено → выключаю оба сиденья");
        autoHeat.stopAll();
        shutdownSeats();
    }

    /**
     * Каскад запускается по зажиганию ON, а не по старту сервиса. ГУ поднимается
     * от открытия двери, но греть в этот момент некого: без нагрузки на сиденье
     * нагреватель не включается (см. NATIVE_MIGRATION.md, «Принятое допущение»).
     * ON — первый момент, когда человек гарантированно сидит.
     */
    private void startAutoHeat() {
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
        levels.put(Seat.DRIVER, 0);
        levels.put(Seat.PASSENGER, 0);
        UiListener listener = uiListener;
        if (listener != null) {
            listener.onSeatLevel(Seat.DRIVER, 0);
            listener.onSeatLevel(Seat.PASSENGER, 0);
        }
        seatsOff = driver && passenger;
        if (!seatsOff) {
            onLog("ВНИМАНИЕ: выключение сидений не подтверждено");
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
