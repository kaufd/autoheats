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
        public boolean isRunning(Preset preset) {
            SeatHeatService service = serviceBinding.get();
            return service != null && service.isPresetRunning(preset);
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
    private PagerAdapter tabsAdapter;
    private TextView[] tabButtons;
    private TextView temperatureView;

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
        temperatureView = findViewById(R.id.temperature);
        pager = findViewById(R.id.pager);

        serviceBinding = new ServiceBindingController(this, this, bindingListener);
        heatUi = new SeatHeatUiController(this, settings, serviceBinding,
                this::openPresetsFor, palette);
        logUi = new LogUiController(this, serviceBinding, palette);
        // Панель создаётся до вкладок: на смену страницы отвечает onPageSelected,
        // и к первому же его вызову она обязана существовать.
        presetsPanel = new PresetsPanel(this, new PresetStore(this), palette, presetListener);

        buildTabs();
        bindSettingsTab();
        heatUi.bind();
        logUi.bind();

        // Слушатели навешаны, динамический UI ещё не собран: его целиком строит
        // applyTheme. Раньше каждая вкладка строилась дважды — сначала здесь,
        // потом ещё раз отсюда же, вместе с чтением всех пресетов из хранилища.
        applyTheme();
        Fonts.applyTo(findViewById(android.R.id.content));

        // Скрытый переключатель отладки — тот же жест, что во Flutter-версии.
        findViewById(R.id.temperaturePill).setOnLongClickListener(v -> {
            toggleDebugMode();
            return true;
        });

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
     * всем, кто рисует. Кнопки не перечисляются поимённо — их находит обход
     * дерева по тегу из @style/PrimaryButton, поэтому новая кнопка в разметке
     * перекрашивается сама.
     */
    private void applyTheme() {
        palette = ThemePalette.of(this, theme);
        findViewById(R.id.background).setBackgroundResource(palette.backgroundRes);
        findViewById(R.id.centerDivider).setBackgroundColor(palette.divider);

        // Плашка температуры бледнее чипов: своя пара значений, и она здесь
        // единственная — роли в ThemePalette заведены только для повторяющихся.
        findViewById(R.id.temperaturePill).setBackground(Ui.roundRect(this, 50,
                Ui.withAlpha(palette.accent, 30), Ui.withAlpha(palette.accent, 100)));

        ImageView icon = findViewById(R.id.temperatureIcon);
        icon.setColorFilter(palette.accent, PorterDuff.Mode.SRC_IN);
        ((ImageView) findViewById(R.id.injectThermometer))
                .setColorFilter(palette.accent, PorterDuff.Mode.SRC_IN);

        renderTabs();
        paintSettingsTab();
        heatUi.applyTheme(palette);
        presetsPanel.applyTheme(palette);
        logUi.applyTheme(palette);
        Ui.paintButtons(findViewById(android.R.id.content), palette);
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
        tabButtons[TAB_LOG].setVisibility(
                settings.debugMode() ? View.VISIBLE : View.GONE);

        // Страницы приходят из разметки уже видимыми, а показывать их решает
        // адаптер: без этого выключенная вкладка логов осталась бы VISIBLE и
        // держалась бы в дереве нерасположенной — то есть невидимой случайно,
        // а не по правилу.
        for (int index = 0; index < pager.getChildCount(); index++) {
            pager.getChildAt(index).setVisibility(View.GONE);
        }

        tabsAdapter = new TabsAdapter();
        pager.setAdapter(tabsAdapter);
        // Четыре статических экрана: держим все разложенными, чтобы
        // activity.findViewById находил их в любой момент, а не только пока
        // вкладка рядом с текущей.
        pager.setOffscreenPageLimit(TAB_LOG);
        pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                onTabShown(position);
            }
        });
        renderTabs();
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
        if (index == TAB_LOG) {
            logUi.onTabShown();
        } else {
            // Лог продолжает копиться, но в свой TextView не пишет: страница
            // остаётся разложенной, и каждая строка стоила бы пересборки
            // разметки на пятистах строках.
            logUi.onTabHidden();
        }
    }

    /** Пресеты того сиденья, чей сегмент нажали, а не всегда водительские. */
    private void openPresetsFor(Seat seat) {
        presetsPanel.selectSeat(seat);
        showTab(TAB_PRESETS);
    }

    /**
     * Страницы уже лежат в разметке, адаптер их не создаёт и не выбрасывает:
     * контроллеры вкладок ищут свои View через activity.findViewById, и
     * страница, вынутая из дерева, стала бы для них null. Поэтому «удаление»
     * страницы — это GONE: скрытая вкладка логов пропадает из листания,
     * оставаясь и в дереве, и под своим контроллером.
     *
     * Отсюда ограничение: прятать можно только последнюю страницу. Позиция в
     * пейджере здесь равна индексу ребёнка, а GONE его не сдвигает — скрытая
     * середина увела бы все страницы правее на одну позицию.
     */
    private final class TabsAdapter extends PagerAdapter {

        @Override
        public int getCount() {
            return (settings.debugMode() ? TAB_LOG : TAB_SETTINGS) + 1;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            View page = container.getChildAt(position);
            page.setVisibility(View.VISIBLE);
            return page;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            ((View) object).setVisibility(View.GONE);
        }

        @Override
        public int getItemPosition(Object object) {
            int index = pager.indexOfChild((View) object);
            // Страница выпала за пределы списка — только так ViewPager узнает,
            // что её пора убрать, когда отладку выключили.
            return index >= 0 && index < getCount() ? index : POSITION_NONE;
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }
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

    private void toggleDebugMode() {
        boolean enabled = !settings.debugMode();
        // Запоминаем до notifyDataSetChanged: убрав страницу, ViewPager сам
        // сдвинет текущую позицию, и спрашивать её потом уже поздно.
        boolean wasOnLog = pager.getCurrentItem() == TAB_LOG;
        settings.setDebugMode(enabled);
        tabButtons[TAB_LOG].setVisibility(enabled ? View.VISIBLE : View.GONE);
        tabsAdapter.notifyDataSetChanged();
        if (!enabled && wasOnLog) {
            showTab(TAB_HEAT);
        }
        Toast.makeText(this, enabled
                ? "Отладка включена — появилась вкладка «Логи»"
                : "Отладка выключена", Toast.LENGTH_SHORT).show();
    }

    // --- вкладка настроек ---

    /** Разовая привязка: слушатели и стартовое состояние, ничего от палитры. */
    private void bindSettingsTab() {
        Switch showTemperature = findViewById(R.id.showTemperature);
        // Слушателя ещё нет, поэтому setChecked никого не дёргает и снимать его
        // на время не нужно: раньше это приходилось делать только потому, что
        // вкладка пересобиралась при каждой смене темы.
        showTemperature.setChecked(settings.showCabinTemperature());
        showTemperature.setOnCheckedChangeListener((button, checked) -> {
            settings.setShowCabinTemperature(checked);
            applyTemperatureVisibility();
        });
        applyTemperatureVisibility();

        findViewById(R.id.enableAutostart).setOnClickListener(v -> requestPermissions());
    }

    /** Всё, что зависит от темы: витрина тем, переключатель, галочка доступа. */
    private void paintSettingsTab() {
        LinearLayout themes = findViewById(R.id.themeSegments);
        themes.removeAllViews();
        for (AppTheme option : AppTheme.values()) {
            themes.addView(themeButton(option));
        }
        paintSwitch(findViewById(R.id.showTemperature));
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

    /**
     * Скрытие через прозрачность, а не INVISIBLE: на этом же пятне живёт
     * длинный тап, включающий отладку. Невидимая View не получает касаний, и
     * человек, спрятавший температуру, не смог бы выключить вкладку «Логи» —
     * пришлось бы сначала возвращать температуру на экран.
     */
    private void applyTemperatureVisibility() {
        findViewById(R.id.temperaturePill)
                .setAlpha(settings.showCabinTemperature() ? 1f : 0f);
    }

    private void renderPermissions() {
        boolean granted = AccessibilityToggle.isEnabled(this);
        ImageView check = findViewById(R.id.permissionsGranted);
        check.setVisibility(granted ? View.VISIBLE : View.GONE);
        check.setColorFilter(palette.accent, PorterDuff.Mode.SRC_IN);
        findViewById(R.id.enableAutostart).setVisibility(granted ? View.GONE : View.VISIBLE);
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
            temperatureView.setText(text);
            temperatureView.setTextColor(color);
            // Вторую подпись на вкладке логов рисует её собственный контроллер:
            // Activity раздаёт событие, а не лезет в чужие View по id.
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
