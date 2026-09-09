package com.wt.airconditioner;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import java.util.Locale;

/**
 * Экран управления. Activity связывает жизненный цикл Android с небольшими
 * контроллерами вкладок; состояние автомобиля живёт в SeatHeatService.
 */
public class MainActivity extends Activity implements SeatHeatService.UiListener {

    private static final int TAB_HEAT = 0;
    private static final int TAB_PRESETS = 1;
    private static final int TAB_SETTINGS = 2;
    private static final int TAB_LOG = 3;
    /** Со скрытой вкладкой логов страниц на одну меньше — она последняя. */
    private static final int TAB_COUNT = 4;

    /** Разметка каждой вкладки, по её позиции. */
    private static final int[] TAB_LAYOUTS = {
            R.layout.tab_heat,
            R.layout.tab_presets,
            R.layout.tab_settings,
            R.layout.tab_logs,
    };

    /**
     * Переключатель по CustomSwitch из Flutter-версии: трек 65×30, ползунок —
     * круг 30 во всю высоту трека. Включённый трек — акцент с прозрачностью
     * 100, выключенный ползунок серый (systemGrey), выключенный трек тёмно-серый
     * (systemGreyDark). Серый в выключенном состоянии — не потеря темы, а
     * оригинальное поведение.
     */
    private static final int TRACK_ALPHA = 100;
    private static final int TRACK_WIDTH_DP = 65;
    private static final int TRACK_HEIGHT_DP = 30;
    private static final int THUMB_SIZE_DP = 30;

    private final ServiceBindingController.Listener bindingListener =
            new ServiceBindingController.Listener() {
                @Override
                public void onConnected(SeatHeatService service,
                        java.util.List<String> logSnapshot) {
                    logUi.setInitialSnapshot(logSnapshot);
                    heatUi.render();
                    // Без сервиса список нарисован с «запустить» на всех
                    // карточках: спросить, что греет, было не у кого.
                    presetsPanel.render();
                }

                @Override
                public void onDisconnected() {
                    heatUi.render();
                    presetsPanel.render();
                }
            };

    private final SeatHeatUiController.Listener heatListener =
            new SeatHeatUiController.Listener() {
                @Override
                public void onPresetsRequested(Seat seat) {
                    openPresetsFor(seat);
                }

                @Override
                public void onDebugToggleRequested() {
                    toggleDebugMode();
                }
            };

    private final PresetsPanel.Listener presetListener = new PresetsPanel.Listener() {
        @Override
        public void onApply(Preset preset) {
            SeatHeatService service = serviceBinding.get();
            if (service != null) {
                service.applyPreset(preset);
                showTab(TAB_HEAT);
                heatUi.render();
            }
        }

        @Override
        public String runningPreset(Seat seat) {
            SeatHeatService service = serviceBinding.get();
            return service == null ? null : service.runningPreset(seat);
        }

        @Override
        public void onStop(Preset preset) {
            SeatHeatService service = serviceBinding.get();
            if (service == null) {
                return;
            }
            if (!service.stopPreset(preset.seat)) {
                Toast.makeText(MainActivity.this, "Подогрев не выключился — снимите его вручную",
                        Toast.LENGTH_LONG).show();
            }
            // Остаёмся на вкладке: человек разбирается со списком, а не ждёт
            // результата — в отличие от запуска, который уводит на сиденья.
            presetsPanel.render();
            heatUi.render();
        }

        @Override
        public void onPresetChanged(String oldEncoded, String newEncoded) {
            SeatHeatService service = serviceBinding.get();
            if (service != null) {
                service.onPresetChanged(oldEncoded, newEncoded);
            }
        }
    };

    private HeatSettings settings;
    private AppTheme theme;
    private ThemePalette palette;

    private ViewPager pager;
    private TextView[] tabButtons;

    /**
     * Живые страницы вкладок, по позиции. null — страницы сейчас нет: так
     * выглядит вкладка логов с выключенной отладкой. Заполняет и чистит
     * TabsAdapter, а читают те, кто красит и обновляет вкладки.
     */
    private final View[] pages = new View[TAB_COUNT];

    private ServiceBindingController serviceBinding;
    private SeatHeatUiController heatUi;
    private LogUiController logUi;
    private PresetsPanel presetsPanel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        settings = new HeatSettings(this);
        theme = settings.theme();
        palette = ThemePalette.of(this, theme);
        pager = findViewById(R.id.pager);

        serviceBinding = new ServiceBindingController(this, this, bindingListener);
        // Контроллеры заводятся без своих View: страницы им раздаст адаптер,
        // как только пейджер их создаст.
        heatUi = new SeatHeatUiController(this, settings, serviceBinding, heatListener, palette);
        logUi = new LogUiController(this, serviceBinding, palette);
        presetsPanel = new PresetsPanel(this, new PresetStore(this), palette, presetListener);

        // Страницы создаются, разбираются контроллерами и красятся внутри
        // setAdapter — снаружи остаются только шапка и фон. Раньше каждая
        // вкладка собиралась дважды: сначала здесь, потом ещё раз из applyTheme.
        buildTabs();
        paintChrome();
        Fonts.applyTo(findViewById(R.id.appBar));

        serviceBinding.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderPermissions();
    }

    @Override
    protected void onDestroy() {
        serviceBinding.destroy();
        // Сервис намеренно не останавливаем: он должен пережить закрытие
        // экрана, иначе автовыключение по зажиганию перестанет работать.
        super.onDestroy();
    }

    // --- оформление ---

    /**
     * Единственная точка смены оформления: палитра собирается один раз и уходит
     * всем, кто рисует, — шапке и каждой живой странице.
     */
    private void applyTheme() {
        palette = ThemePalette.of(this, theme);
        paintChrome();
        for (int position = 0; position < TAB_COUNT; position++) {
            paintPage(position);
        }
    }

    /** Всё, что живёт вне пейджера и потому не принадлежит ни одной вкладке. */
    private void paintChrome() {
        findViewById(R.id.background).setBackgroundResource(palette.backgroundRes);
        renderTabs();
    }

    /**
     * Красит одну страницу: сначала кнопки, потом её контроллер. Кнопки не
     * перечисляются поимённо — их находит обход страницы по тегу из
     * @style/PrimaryButton, поэтому новая кнопка в разметке перекрашивается
     * сама.
     */
    private void paintPage(int position) {
        View page = pages[position];
        if (page == null) {
            return;
        }
        Ui.paintButtons(page, palette);
        switch (position) {
            case TAB_HEAT:
                heatUi.applyTheme(palette);
                break;
            case TAB_PRESETS:
                presetsPanel.applyTheme(palette);
                break;
            case TAB_SETTINGS:
                paintSettingsTab();
                break;
            case TAB_LOG:
                logUi.applyTheme(palette);
                break;
            default:
                break;
        }
    }

    /**
     * Системный Switch рисуется дефолтным colorAccent платформы: при смене темы
     * он оставался бирюзовым посреди красного экрана.
     *
     * Тинтом это не лечится — штатный трек тёмный и полупрозрачный, на чёрном
     * фоне головы он не читается ни в каком цвете (проверено на эмуляторе:
     * после setTrackTintList виден один ползунок). Поэтому и трек, и ползунок
     * рисуются свои, как и остальные элементы этого экрана.
     */
    private void paintSwitch(Switch view) {
        int trackOff = getResources().getColor(R.color.system_grey_dark);
        view.setTrackDrawable(switchPart(
                blend(palette.accent, TRACK_ALPHA, trackOff), trackOff,
                TRACK_WIDTH_DP, TRACK_HEIGHT_DP, GradientDrawable.RECTANGLE));
        view.setThumbDrawable(switchPart(
                palette.accent, getResources().getColor(R.color.system_grey),
                THUMB_SIZE_DP, THUMB_SIZE_DP, GradientDrawable.OVAL));
        // Штатные отступы Switch рассчитаны на его собственные 9-patch: с
        // нашими фигурами они добавляют пустое поле сбоку от трека.
        view.setThumbTextPadding(0);
        view.setSwitchMinWidth(Ui.dp(this, TRACK_WIDTH_DP));

        // Свежему StateListDrawable состояние не передаётся: setThumbDrawable
        // только запоминает его и просит перерисовку, а state приходит из
        // drawableStateChanged(). После смены темы его никто не вызывает —
        // setChecked() с тем же значением выходит сразу, — и переключатель
        // оставался серым до первого касания.
        view.refreshDrawableState();
        view.jumpDrawablesToCurrentState();
    }

    /**
     * Акцент, положенный с прозрачностью на непрозрачную подложку. Оригинал
     * рисует включённый трек как primary.withAlpha(100) поверх фона, но фон
     * головы почти чёрный, а красный акцент (#951019) сам по себе тёмный: в
     * сумме трек пропадал, и переключатель выглядел рабочим только в зелёной
     * теме. Подложка — тот же серый, что у выключенного трека, поэтому оттенок
     * темы сохраняется, а видимость больше не зависит от яркости акцента.
     */
    private static int blend(int foreground, int alpha, int background) {
        float weight = alpha / 255f;
        return Color.rgb(
                Math.round(Color.red(foreground) * weight + Color.red(background) * (1 - weight)),
                Math.round(Color.green(foreground) * weight
                        + Color.green(background) * (1 - weight)),
                Math.round(Color.blue(foreground) * weight + Color.blue(background) * (1 - weight)));
    }

    /** Форма для включённого и выключенного состояния — трек или ползунок. */
    private Drawable switchPart(int checkedColor, int uncheckedColor,
            int widthDp, int heightDp, int shape) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_checked},
                switchShape(checkedColor, widthDp, heightDp, shape));
        states.addState(new int[]{}, switchShape(uncheckedColor, widthDp, heightDp, shape));
        return states;
    }

    private GradientDrawable switchShape(int color, int widthDp, int heightDp, int form) {
        GradientDrawable shape = form == GradientDrawable.RECTANGLE
                ? Ui.roundRect(this, heightDp / 2f, color)
                : Ui.oval(color);
        shape.setSize(Ui.dp(this, widthDp), Ui.dp(this, heightDp));
        return shape;
    }

    // --- вкладки ---

    private void buildTabs() {
        tabButtons = new TextView[]{
                findViewById(R.id.tabHeat),
                findViewById(R.id.tabPresets),
                findViewById(R.id.tabSettings),
                findViewById(R.id.tabLog),
        };
        for (int index = 0; index < tabButtons.length; index++) {
            final int position = index;
            tabButtons[index].setOnClickListener(v -> showTab(position));
        }
        renderDebugTab();

        // Четыре статических экрана: держим все разложенными. Так страница
        // живёт ровно столько же, сколько её контроллер, и пересобирать её при
        // каждом листании не приходится.
        pager.setOffscreenPageLimit(TAB_COUNT - 1);
        pager.setAdapter(new TabsAdapter());
        pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                onTabShown(position);
            }
        });
    }

    /**
     * Переход на вкладку. Кламп по числу страниц делает сам ViewPager, поэтому
     * сюда можно звать с любым индексом.
     */
    private void showTab(int index) {
        if (pager.getCurrentItem() == index) {
            // На текущей странице setCurrentItem выходит сразу, и onPageSelected
            // не придёт. Повторное нажатие всё равно должно освежать вкладку:
            // им возвращают лог к последней строке, промотав его вверх.
            onTabShown(index);
            return;
        }
        pager.setCurrentItem(index, true);
    }

    /**
     * Вкладка стала видимой — неважно, по кнопке или смахиванием. Всё, что надо
     * освежить при показе, живёт здесь: у ViewPager это единственная точка, куда
     * приходят оба пути.
     */
    private void onTabShown(int index) {
        renderTabs();
        if (index == TAB_PRESETS) {
            // Кнопка play/pause зависит от того, что сейчас на сиденьях, а меняют
            // это на соседней вкладке: список должен свериться на каждом показе.
            presetsPanel.render();
        }
        // Скрытый лог продолжает копиться, но в свой TextView не пишет:
        // страница остаётся разложенной, и каждая строка стоила бы пересборки
        // разметки на пятистах строках.
        logUi.setTabVisible(index == TAB_LOG);
    }

    /** Пресеты того сиденья, чей сегмент нажали, а не всегда водительские. */
    private void openPresetsFor(Seat seat) {
        presetsPanel.selectSeat(seat);
        showTab(TAB_PRESETS);
    }

    /**
     * Создаёт страницы вкладок и раздаёт их контроллерам. Каждая страница —
     * отдельная разметка: контроллер получает её корень и ищет свои View
     * внутри него, поэтому активити не обязана держать все вкладки в дереве
     * ради чужих findViewById.
     */
    private final class TabsAdapter extends PagerAdapter {

        @Override
        public int getCount() {
            return settings.debugMode() ? TAB_COUNT : TAB_COUNT - 1;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            View page = getLayoutInflater().inflate(TAB_LAYOUTS[position], container, false);
            container.addView(page);
            pages[position] = page;
            bindPage(position, page);
            return page;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((View) object);
            pages[position] = null;
            if (position == TAB_LOG) {
                logUi.unbind();
            }
        }

        @Override
        public int getItemPosition(Object object) {
            for (int position = 0; position < getCount(); position++) {
                if (pages[position] == object) {
                    return position;
                }
            }
            // Страница выпала за пределы списка — только так ViewPager узнает,
            // что её пора убрать, когда отладку выключили.
            return POSITION_NONE;
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }
    }

    /**
     * Свежая страница уходит своему контроллеру и сразу красится: вкладка логов
     * появляется по ходу работы, и ждать следующей смены темы ей нельзя.
     */
    private void bindPage(int position, View page) {
        switch (position) {
            case TAB_HEAT:
                heatUi.bind(page);
                break;
            case TAB_PRESETS:
                presetsPanel.bind(page);
                break;
            case TAB_SETTINGS:
                bindSettingsTab(page);
                break;
            case TAB_LOG:
                logUi.bind(page);
                break;
            default:
                break;
        }
        Fonts.applyTo(page);
        paintPage(position);
    }

    private void renderTabs() {
        if (tabButtons == null) {
            return;
        }
        int current = pager.getCurrentItem();
        for (int index = 0; index < tabButtons.length; index++) {
            boolean selected = index == current;
            tabButtons[index].setBackground(Ui.roundRect(this, Ui.BUTTON_RADIUS_DP,
                    selected ? palette.accent : Color.TRANSPARENT));
            tabButtons[index].setTextColor(selected ? palette.textOnAccent : Color.WHITE);
        }
    }

    /**
     * Кнопка вкладки логов и число страниц у адаптера — одно правило, поэтому
     * читают его тут вдвоём: разъехавшись, они дали бы кнопку без страницы.
     */
    private void renderDebugTab() {
        tabButtons[TAB_LOG].setVisibility(settings.debugMode() ? View.VISIBLE : View.GONE);
        PagerAdapter adapter = pager.getAdapter();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void toggleDebugMode() {
        boolean enabled = !settings.debugMode();
        // Запоминаем до notifyDataSetChanged: убрав страницу, ViewPager сам
        // сдвинет текущую позицию, и спрашивать её потом уже поздно.
        boolean wasOnLog = pager.getCurrentItem() == TAB_LOG;
        settings.setDebugMode(enabled);
        renderDebugTab();
        if (!enabled && wasOnLog) {
            showTab(TAB_HEAT);
        }
        Toast.makeText(this, enabled
                ? "Отладка включена — появилась вкладка «Логи»"
                : "Отладка выключена", Toast.LENGTH_SHORT).show();
    }

    // --- вкладка настроек ---

    /** Разовая привязка: слушатели и стартовое состояние, ничего от палитры. */
    private void bindSettingsTab(View page) {
        Switch showTemperature = page.findViewById(R.id.showTemperature);
        // Слушателя ещё нет, поэтому setChecked никого не дёргает и снимать его
        // на время не нужно: раньше это приходилось делать только потому, что
        // вкладка пересобиралась при каждой смене темы.
        showTemperature.setChecked(settings.showCabinTemperature());
        showTemperature.setOnCheckedChangeListener((button, checked) -> {
            settings.setShowCabinTemperature(checked);
            // Плашка живёт на соседней вкладке и принадлежит её контроллеру.
            heatUi.applyTemperatureVisibility();
        });

        page.findViewById(R.id.enableAutostart).setOnClickListener(v -> requestPermissions());
    }

    /** Всё, что зависит от темы: витрина тем, переключатель, галочка доступа. */
    private void paintSettingsTab() {
        View page = pages[TAB_SETTINGS];
        LinearLayout themes = page.findViewById(R.id.themeSegments);
        themes.removeAllViews();
        for (AppTheme option : AppTheme.values()) {
            themes.addView(themeButton(option));
        }
        paintSwitch(page.findViewById(R.id.showTemperature));
        renderPermissions();
    }

    private TextView themeButton(AppTheme option) {
        TextView button = new TextView(this);
        button.setText(option.title);
        button.setTextSize(16);
        button.setGravity(android.view.Gravity.CENTER);
        button.setTypeface(Fonts.regular(this));
        button.setPadding(Ui.dp(this, 28), 0, Ui.dp(this, 28), 0);

        // Каждая кнопка показывает цвет своей темы, а не текущей: это витрина,
        // поэтому палитра берётся по опции, а не берётся поле palette.
        boolean selected = option == theme;
        ThemePalette optionPalette = ThemePalette.of(this, option);
        button.setBackground(Ui.roundRect(this, Ui.BUTTON_RADIUS_DP,
                selected ? optionPalette.accent : Color.TRANSPARENT,
                selected ? optionPalette.accent : Color.WHITE));
        button.setTextColor(selected ? optionPalette.textOnAccent : Color.WHITE);

        button.setOnClickListener(v -> {
            theme = option;
            settings.setTheme(option);
            applyTheme();
        });

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(this, 44));
        params.setMarginStart(Ui.dp(this, 12));
        button.setLayoutParams(params);
        return button;
    }

    private void renderPermissions() {
        View page = pages[TAB_SETTINGS];
        if (page == null) {
            return;
        }
        boolean granted = AccessibilityToggle.isEnabled(this);
        ImageView check = page.findViewById(R.id.permissionsGranted);
        check.setVisibility(granted ? View.VISIBLE : View.GONE);
        check.setColorFilter(palette.accent, PorterDuff.Mode.SRC_IN);
        page.findViewById(R.id.enableAutostart).setVisibility(granted ? View.GONE : View.VISIBLE);
    }

    private void requestPermissions() {
        if (AccessibilityToggle.enableWithoutUi(this)) {
            logUi.log("замер: служба доступности включена программно (WRITE_SECURE_SETTINGS выдан)");
            renderPermissions();
            return;
        }
        logUi.log("замер: программно включить не удалось, открываю «Спец. возможности»");
        if (!AccessibilityToggle.openSettings(this)) {
            Toast.makeText(this, "Экран «Спец. возможности» не открылся — "
                    + "включите службу AutoHeat вручную", Toast.LENGTH_LONG).show();
            logUi.log("ВНИМАНИЕ: экран «Спец. возможности» не открылся");
        }
    }

    // --- SeatHeatService.UiListener ---

    @Override
    public void onLogLine(String line) {
        logUi.onLogLine(line);
    }

    @Override
    public void onHvacReady(boolean ready) {
        // UI намеренно не показывает отдельный индикатор связи.
    }

    @Override
    public void onCabinTemperature(double celsius) {
        runOnUiThread(() -> {
            String text = String.format(Locale.US, "%.1f °C", celsius);
            int color = temperatureColor(celsius);
            // Обе подписи рисуют контроллеры своих вкладок: Activity раздаёт
            // событие, а не лезет в чужие View по id.
            heatUi.onCabinTemperature(text, color);
            logUi.onCabinTemperature(text, color);
        });
    }

    @Override
    public void onSeatLevel(Seat seat, int level) {
        // Уровень не передаём: сервис уже записал подтверждённое состояние, и
        // контроллер перечитывает у него оба сиденья, а не хранит свою копию.
        runOnUiThread(heatUi::render);
    }

    private int temperatureColor(double celsius) {
        if (celsius <= -5) {
            return getResources().getColor(R.color.temp_cold);
        }
        if (celsius <= 5) {
            return getResources().getColor(R.color.temp_cool);
        }
        if (celsius <= 25) {
            return getResources().getColor(R.color.temp_warm);
        }
        return getResources().getColor(R.color.accent_red);
    }
}
