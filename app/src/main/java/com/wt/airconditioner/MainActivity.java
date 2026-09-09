package com.wt.airconditioner;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import java.util.List;

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

    private final ServiceBindingController.Listener bindingListener =
            new ServiceBindingController.Listener() {
                @Override
                public void onConnected(SeatHeatService service, List<String> logSnapshot) {
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

    private final SettingsUiController.Listener settingsListener =
            new SettingsUiController.Listener() {
                @Override
                public void onThemeSelected(AppTheme theme) {
                    // Сохраняем до applyTheme: палитра собирается по настройке,
                    // и другого хранилища выбранной темы больше нет.
                    settings.setTheme(theme);
                    applyTheme();
                }

                @Override
                public void onTemperatureVisibilityChanged() {
                    heatUi.applyTemperatureVisibility();
                }

                @Override
                public void onLog(String message) {
                    logUi.log(message);
                }
            };

    private HeatSettings settings;
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
    private SettingsUiController settingsUi;
    private AppUpdateController updateController;

    /**
     * Те же контроллеры, но по позиции вкладки — порядок обязан совпадать с
     * TAB_LAYOUTS и константами TAB_*. Отдельные поля выше остались потому, что
     * у каждой вкладки есть и свои вызовы, которых нет в TabController.
     */
    private TabController[] tabs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        settings = new HeatSettings(this);
        palette = ThemePalette.of(this, settings.theme());
        pager = findViewById(R.id.pager);

        serviceBinding = new ServiceBindingController(this, this, bindingListener);
        // Контроллеры заводятся без своих View: страницы им раздаст адаптер,
        // как только пейджер их создаст.
        heatUi = new SeatHeatUiController(this, settings, serviceBinding, heatListener, palette);
        logUi = new LogUiController(this, serviceBinding, palette);
        presetsPanel = new PresetsPanel(this, new PresetStore(this), palette, presetListener);
        updateController = new AppUpdateController(this);
        settingsUi = new SettingsUiController(this, settings, updateController,
                settingsListener, palette);
        tabs = new TabController[]{heatUi, presetsPanel, settingsUi, logUi};

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
        // Доступ могли выдать в системных настройках и вернуться сюда.
        settingsUi.renderPermissions();
        updateController.onResume();
    }

    @Override
    protected void onDestroy() {
        updateController.destroy();
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
        palette = ThemePalette.of(this, settings.theme());
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
        tabs[position].applyTheme(palette);
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
        // Кнопки вкладок живут вне пейджера и потому остаются за Activity;
        // что делать самой вкладке, знает её контроллер.
        renderTabs();
        for (int position = 0; position < tabs.length; position++) {
            tabs[position].onTabVisible(position == index);
        }
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
        tabs[position].bind(page);
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
            String text = TemperatureConstants.celsiusText(celsius);
            int color = Ui.temperatureColor(this, celsius);
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

}
