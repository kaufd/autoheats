package com.wt.airconditioner;

import android.app.Activity;
import android.view.View;
import android.widget.LinearLayout;

/** Управляет только главной вкладкой: два фиксированных сиденья и их controls. */
final class SeatHeatUiController {

    interface Listener {
        void onPresetsRequested();
    }

    private static final int[] LEVEL_ORDER = {1, 2, 3, 0};
    private static final String[] LEVEL_TITLES = {"1", "2", "3", "OFF"};

    private static final int MODE_TEXT_SP = 19;
    private static final int MODE_HEIGHT_DP = 46;
    private static final int MODE_PADDING_DP = 20;
    private static final int LEVEL_TEXT_SP = 14;
    private static final int LEVEL_HEIGHT_DP = 29;
    private static final int LEVEL_PADDING_DP = 4;

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

    SeatHeatUiController(Activity activity, HeatSettings settings,
            ServiceBindingController serviceProvider, Listener listener, ThemePalette palette) {
        this.activity = activity;
        this.settings = settings;
        this.serviceProvider = serviceProvider;
        this.listener = listener;
        this.palette = palette;
    }

    void bind() {
        for (SeatView seatView : SEAT_VIEWS) {
            activity.findViewById(seatView.seatId)
                    .setOnClickListener(v -> toggleSeat(seatView.seat));
        }
    }

    void applyTheme(ThemePalette palette) {
        this.palette = palette;
        render();
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
        SegmentedControl.build(activity.findViewById(seatView.modesId), modes, mode.ordinal(),
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
        LinearLayout levels = activity.findViewById(seatView.levelsId);
        SegmentedControl.build(levels, levelItems, selected, palette,
                LEVEL_TEXT_SP, LEVEL_HEIGHT_DP, LEVEL_PADDING_DP,
                index -> setLevel(seat, LEVEL_ORDER[index]));
        // В неручных режимах controls остаются невидимыми, но сохраняют место.
        levels.setVisibility(mode == HeatMode.MANUAL ? View.VISIBLE : View.INVISIBLE);
        renderDots(activity.findViewById(seatView.dotsId), level);
    }

    private void renderDots(LinearLayout container, int level) {
        container.removeAllViews();
        for (int index = 0; index < 3; index++) {
            View dot = new View(activity);
            dot.setBackground(Ui.oval(level > index ? palette.accent
                    : activity.getResources().getColor(R.color.system_grey)));

            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(Ui.dp(activity, 20), Ui.dp(activity, 20));
            params.setMarginStart(Ui.dp(activity, 4));
            params.setMarginEnd(Ui.dp(activity, 4));
            container.addView(dot, params);
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
                listener.onPresetsRequested();
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
