package com.wt.airconditioner;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Управляет только главной вкладкой: два фиксированных сиденья и их controls. */
final class SeatHeatUiController implements TabController {

    interface Listener {
        /** Пресетов у сиденья нет — человека надо отправить их выбирать. */
        void onPresetsRequested(Seat seat);

        /** Длинный тап по температуре — скрытый жест включения отладки. */
        void onDebugToggleRequested();
    }

    private static final int[] LEVEL_ORDER = {1, 2, 3, 0};
    private static final String[] LEVEL_TITLES = {"1", "2", "3", "OFF"};

    /** Размеры переключателя режима. */
    private static final int MODE_TEXT_SP = 19;
    private static final int MODE_HEIGHT_DP = 46;
    private static final int MODE_PADDING_DP = 20;

    /** Крупнее переключателя режима намеренно: уровень жмут на ходу и чаще всего. */
    private static final int LEVEL_TEXT_SP = 20;
    private static final int LEVEL_HEIGHT_DP = 50;
    private static final int LEVEL_PADDING_DP = 8;
    /** Индикатор уровня — три точки. */
    private static final int DOTS = 3;
    /** Толщина кольца выключенной точки. */
    private static final int DOT_RING_DP = 2;

    private static final SeatView[] SEAT_VIEWS = {
            new SeatView(Seat.DRIVER, R.id.driverSeat, R.id.driverModes,
                    R.id.driverLevels, R.id.driverDots),
            new SeatView(Seat.PASSENGER, R.id.passengerSeat, R.id.passengerModes,
                    R.id.passengerLevels, R.id.passengerDots),
    };

    private final Activity activity;
    private final HeatSettings settings;
    private final ServiceBindingController serviceProvider;
    private final Listener listener;
    private ThemePalette palette;

    private View page;
    private View temperaturePill;
    private TextView temperatureView;

    SeatHeatUiController(Activity activity, HeatSettings settings,
            ServiceBindingController serviceProvider, Listener listener, ThemePalette palette) {
        this.activity = activity;
        this.settings = settings;
        this.serviceProvider = serviceProvider;
        this.listener = listener;
        this.palette = palette;
    }

    @Override
    public void bind(View page) {
        this.page = page;
        temperaturePill = page.findViewById(R.id.temperaturePill);
        temperatureView = page.findViewById(R.id.temperature);
        for (SeatView seatView : SEAT_VIEWS) {
            page.findViewById(seatView.seatId)
                    .setOnClickListener(v -> toggleSeat(seatView.seat));
        }
        temperaturePill.setOnLongClickListener(v -> {
            listener.onDebugToggleRequested();
            return true;
        });
        applyTemperatureVisibility();
    }

    @Override
    public void applyTheme(ThemePalette palette) {
        this.palette = palette;
        page.findViewById(R.id.centerDivider).setBackgroundColor(palette.divider);
        /**
         * Плашка бледнее чипов: своя пара значений, здесь единственная.
         */
        temperaturePill.setBackground(Ui.roundRect(activity, 50,
                Ui.withAlpha(palette.accent, 30), Ui.withAlpha(palette.accent, 100)));
        ((ImageView) page.findViewById(R.id.temperatureIcon))
                .setColorFilter(palette.accent, PorterDuff.Mode.SRC_IN);
        render();
    }

    /**
     * Главной вкладке показ ничего не даёт: её содержимое обновляют события
     * сервиса — уровень сиденья и температура, — а они приходят и на скрытой.
     */
    @Override
    public void onTabVisible(boolean visible) {
    }

    /** Температура в салоне — тем же текстом, что и рядом с инжектором логов. */
    void onCabinTemperature(String text, int color) {
        temperatureView.setText(text);
        temperatureView.setTextColor(color);
    }

    /**
     * Скрытие через прозрачность, а не INVISIBLE: на этом же пятне живёт
     * длинный тап, включающий отладку. Невидимая View не получает касаний, и
     * человек, спрятавший температуру, не смог бы выключить вкладку «Логи» —
     * пришлось бы сначала возвращать температуру на экран.
     */
    void applyTemperatureVisibility() {
        temperaturePill.setAlpha(settings.showCabinTemperature() ? 1f : 0f);
    }

    void render() {
        for (SeatView seatView : SEAT_VIEWS) {
            renderSeat(seatView);
        }
    }

    private void renderSeat(SeatView seatView) {
        Seat seat = seatView.seat;
        HeatMode mode = modeOf(seat);
        SegmentedControl.Item[] modes = {
                new SegmentedControl.Item(HeatMode.MANUAL.title, R.drawable.ic_touch_app),
                new SegmentedControl.Item(HeatMode.PRESETS.title, R.drawable.ic_settings),
                new SegmentedControl.Item(HeatMode.AUTO.title, R.drawable.ic_auto),
        };
        SegmentedControl.render(page.findViewById(seatView.modesId), modes, mode.ordinal(),
                palette, MODE_TEXT_SP, MODE_HEIGHT_DP, MODE_PADDING_DP,
                index -> selectMode(seat, HeatMode.values()[index]));

        int level = levelOf(seat);
        int selected = -1;
        SegmentedControl.Item[] levelItems = new SegmentedControl.Item[LEVEL_ORDER.length];
        for (int index = 0; index < LEVEL_ORDER.length; index++) {
            levelItems[index] = new SegmentedControl.Item(LEVEL_TITLES[index]);
            if (LEVEL_ORDER[index] == level) {
                selected = index;
            }
        }
        LinearLayout levels = page.findViewById(seatView.levelsId);
        SegmentedControl.render(levels, levelItems, selected, palette,
                LEVEL_TEXT_SP, LEVEL_HEIGHT_DP, LEVEL_PADDING_DP,
                index -> setLevel(seat, LEVEL_ORDER[index]));
        /**
         * В неручных режимах controls остаются невидимыми, но сохраняют место.
         */
        levels.setVisibility(mode == HeatMode.MANUAL ? View.VISIBLE : View.INVISIBLE);
        renderDots(page.findViewById(seatView.dotsId), level);
    }

    /**
     * Точки собираются один раз: меняется только заливка, а render() зовётся на
     * каждый шаг каскада.
     *
     * Выключенная точка — кольцо, а не серый кружок: в белой теме акцент сам
     * светло-серый, и три включённые точки выглядели ровно как три выключенные.
     * Форма темы не касается и надёжнее любого оттенка.
     */
    private void renderDots(LinearLayout container, int level) {
        if (container.getChildCount() != DOTS) {
            container.removeAllViews();
            for (int index = 0; index < DOTS; index++) {
                View dot = new View(activity);
                dot.setBackground(Ui.oval(Color.TRANSPARENT));
                LinearLayout.LayoutParams params =
                        new LinearLayout.LayoutParams(Ui.dp(activity, 20), Ui.dp(activity, 20));
                params.setMarginStart(Ui.dp(activity, 4));
                params.setMarginEnd(Ui.dp(activity, 4));
                container.addView(dot, params);
            }
        }
        int off = Ui.color(activity, R.color.system_grey);
        int ring = Ui.dp(activity, DOT_RING_DP);
        for (int index = 0; index < DOTS; index++) {
            GradientDrawable shape =
                    (GradientDrawable) container.getChildAt(index).getBackground();
            boolean on = level > index;
            shape.setColor(on ? palette.accent : Color.TRANSPARENT);
            /**
             * Ширина обводки постоянна: у включённой точки она того же цвета,
             * что и заливка, и потому не видна.
             */
            shape.setStroke(ring, on ? palette.accent : off);
        }
    }

    private int levelOf(Seat seat) {
        SeatHeatService service = serviceProvider.get();
        return service == null ? settings.manualLevel(seat) : service.levelOf(seat);
    }

    private HeatMode modeOf(Seat seat) {
        SeatHeatService service = serviceProvider.get();
        return service == null ? settings.mode(seat) : service.modeOf(seat);
    }

    private void selectMode(Seat seat, HeatMode mode) {
        SeatHeatService service = serviceProvider.get();
        if (service == null) {
            return;
        }
        if (mode == HeatMode.PRESETS) {
            if (!service.applyActivePreset(seat)) {
                listener.onPresetsRequested(seat);
            }
            render();
            return;
        }
        service.setMode(seat, mode);
        render();
    }

    private void setLevel(Seat seat, int level) {
        SeatHeatService service = serviceProvider.get();
        if (service == null) {
            return;
        }
        service.setManualLevel(seat, level);
        render();
    }

    private void toggleSeat(Seat seat) {
        SeatHeatService service = serviceProvider.get();
        if (service == null) {
            return;
        }
        if (service.modeOf(seat) != HeatMode.MANUAL) {
            service.setMode(seat, HeatMode.MANUAL);
            service.setManualLevel(seat, 1);
        } else {
            int level = service.levelOf(seat);
            service.setManualLevel(seat, level >= 3 ? 0 : level + 1);
        }
        render();
    }

    private static final class SeatView {
        final Seat seat;
        final int seatId;
        final int modesId;
        final int levelsId;
        final int dotsId;

        SeatView(Seat seat, int seatId, int modesId, int levelsId, int dotsId) {
            this.seat = seat;
            this.seatId = seatId;
            this.modesId = modesId;
            this.levelsId = levelsId;
            this.dotsId = dotsId;
        }
    }
}
