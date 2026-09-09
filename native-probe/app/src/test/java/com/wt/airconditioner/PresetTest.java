package com.wt.airconditioner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Хранение пресетов. Проверяется тестом, потому что порча этих строк тихая:
 * пресеты просто исчезнут из списка, и человек узнает об этом зимой, когда
 * подогрев не включится по его расписанию.
 */
public class PresetTest {

    private static Preset preset(String name, Seat seat) {
        return new Preset(name, seat, new PresetSettings(5.0, 3, 2, 1));
    }

    @Test
    public void survivesEncodeDecode() {
        Preset decoded = Preset.decode(preset("Утро", Seat.DRIVER).encode());

        assertEquals("Утро", decoded.name);
        assertEquals(Seat.DRIVER, decoded.seat);
        assertEquals(3, decoded.settings.sequence.level3Minutes);
        assertEquals(2, decoded.settings.sequence.level2Minutes);
        assertEquals(1, decoded.settings.sequence.level1Minutes);
        assertEquals(5.0, decoded.settings.thresholdCelsius, 0.001);
    }

    /**
     * Имя вводит человек, и в нём может оказаться что угодно, кроме
     * разделителя — его с клавиатуры не ввести. Разделитель строк проверяем
     * отдельно: перевод строки в имени разорвал бы запись надвое и утащил
     * следующий пресет.
     */
    @Test
    public void keepsNameWithPunctuation() {
        Preset decoded = Preset.decode(preset("Утро — «тёплое», 100%", Seat.PASSENGER).encode());
        assertEquals("Утро — «тёплое», 100%", decoded.name);
        assertEquals(Seat.PASSENGER, decoded.seat);
    }

    @Test
    public void keepsOrderOfList() {
        List<Preset> presets = Arrays.asList(
                preset("Первый", Seat.DRIVER),
                preset("Второй", Seat.PASSENGER),
                preset("Третий", Seat.DRIVER));

        List<Preset> restored = Preset.decodeAll(Preset.encodeAll(presets));

        assertEquals(3, restored.size());
        assertEquals("Первый", restored.get(0).name);
        assertEquals("Второй", restored.get(1).name);
        assertEquals("Третий", restored.get(2).name);
    }

    /** Битая строка не должна уносить с собой остальные пресеты. */
    @Test
    public void skipsBrokenRecordsInsteadOfLosingEverything() {
        String text = preset("Целый", Seat.DRIVER).encode() + "\nмусор\n"
                + preset("Тоже целый", Seat.PASSENGER).encode();

        List<Preset> restored = Preset.decodeAll(text);

        assertEquals(2, restored.size());
        assertEquals("Целый", restored.get(0).name);
        assertEquals("Тоже целый", restored.get(1).name);
    }

    @Test
    public void rejectsGarbage() {
        assertNull(Preset.decode(null));
        assertNull(Preset.decode(""));
        assertNull(Preset.decode("Имя\u001FНЕ_СИДЕНЬЕ\u001F3\u001F2\u001F1\u001F5.0"));
        assertNull(Preset.decode("Имя\u001FDRIVER\u001Fне_число\u001F2\u001F1\u001F5.0"));
        assertTrue(Preset.decodeAll("").isEmpty());
        assertTrue(Preset.decodeAll(null).isEmpty());
    }

    /**
     * Имя — граница доверия: его набирает человек за рулём, на сенсорном экране
     * головы. Пресет без имени не найти в списке, а пресет из одних нулей
     * выглядел бы сохранённым и ничего не грел. Длительности и порог приходят
     * слайдерами и негодными быть не могут.
     */
    @Test
    public void rejectsUnusableForm() {
        assertNull("без имени", Preset.fromInput("", Seat.DRIVER, 3, 2, 1, 5));
        assertNull("имя из пробелов", Preset.fromInput("   ", Seat.DRIVER, 3, 2, 1, 5));
        assertNull("нулевое расписание",
                Preset.fromInput("Пустой", Seat.DRIVER, 0, 0, 0, 5));
    }

    /** Нулевой верхний уровень — осмысленный пресет «сразу с двойки», не отказ. */
    @Test
    public void acceptsScheduleStartingBelowTopLevel() {
        Preset skipped = Preset.fromInput("Со двойки", Seat.PASSENGER, 0, 5, 3, -2.5);
        assertEquals(0, skipped.settings.sequence.level3Minutes);
        assertEquals(5, skipped.settings.sequence.level2Minutes);
        assertEquals(-2.5, skipped.settings.thresholdCelsius, 0.001);

        Preset trimmed = Preset.fromInput(" Утро ", Seat.DRIVER, 3, 2, 1, 5);
        assertEquals("имя обрезается", "Утро", trimmed.name);
    }

    /**
     * Перевод строки в имени — единственный символ, способный разорвать
     * хранилище: записи разделены им же. Вставка из буфера или USB-клавиатура
     * его пропускают, и без замены пресет пропал бы вместе с соседним.
     */
    @Test
    public void nameWithNewlineStaysOneRecord() {
        Preset broken = Preset.fromInput("Утро\nв гараже", Seat.DRIVER, 3, 2, 1, 5);
        Preset neighbour = Preset.fromInput("Вечер", Seat.PASSENGER, 3, 2, 1, 5);

        assertEquals("Утро в гараже", broken.name);
        assertEquals("обе записи читаются обратно", 2,
                Preset.decodeAll(Preset.encodeAll(Arrays.asList(broken, neighbour))).size());
    }

    /** Длительность уровня ограничена 15 минутами — потолок из Flutter-версии. */
    @Test
    public void clampsLevelDurations() {
        Preset decoded = Preset.decode(
                new Preset("Долгий", Seat.DRIVER, new PresetSettings(5.0, 99, -4, 1)).encode());

        assertEquals(15, decoded.settings.sequence.level3Minutes);
        assertEquals(0, decoded.settings.sequence.level2Minutes);
    }
}
