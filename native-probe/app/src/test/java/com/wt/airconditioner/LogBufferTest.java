package com.wt.airconditioner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Кольцевой буфер диагностики. Раньше это жило внутри Service и потому не
 * проверялось ничем: на голове нет adb, и лог — единственный способ понять,
 * что произошло, так что его потеря стоит дороже, чем кажется.
 */
public class LogBufferTest {

    private LogBuffer buffer;
    private List<String> delivered;

    @Before
    public void setUp() {
        buffer = new LogBuffer();
        delivered = new ArrayList<>();
    }

    /** Буфер кольцевой: старые строки вытесняются, новые остаются. */
    @Test
    public void keepsLastLinesWithinCapacity() {
        for (int index = 0; index < LogBuffer.CAPACITY + 50; index++) {
            buffer.append("строка " + index);
        }

        assertEquals(LogBuffer.CAPACITY, buffer.size());
        List<String> snapshot = buffer.subscribe(line -> {
        });
        assertTrue("первой осталась 50-я", snapshot.get(0).endsWith("строка 50"));
        assertTrue("последней — самая свежая",
                snapshot.get(snapshot.size() - 1).endsWith("строка 549"));
    }

    /**
     * Подписка отдаёт снимок и дальше шлёт только новое. Иначе экран, открытый
     * во время работы, показал бы часть строк дважды.
     */
    @Test
    public void subscriberGetsSnapshotThenOnlyNewLines() {
        buffer.append("до подписки");
        List<String> snapshot = buffer.subscribe(delivered::add);
        buffer.append("после подписки");

        assertEquals(1, snapshot.size());
        assertTrue(snapshot.get(0).endsWith("до подписки"));
        assertEquals(1, delivered.size());
        assertTrue(delivered.get(0).endsWith("после подписки"));
    }

    /** Отписка обязана прекратить доставку: экран уже закрыт. */
    @Test
    public void unsubscribedListenerStopsReceiving() {
        buffer.subscribe(delivered::add);
        buffer.unsubscribe();
        buffer.append("в пустоту");

        assertTrue(delivered.isEmpty());
        assertEquals("но в буфере строка осталась", 1, buffer.size());
    }

    /** Очистка чистит источник, а не только представление. */
    @Test
    public void clearEmptiesBuffer() {
        buffer.append("что-то");
        buffer.clear();

        assertEquals(0, buffer.size());
        assertTrue(buffer.subscribe(delivered::add).isEmpty());
    }

    /** К строке добавляется время — по нему в логе и ищут момент события. */
    @Test
    public void prefixesEachLineWithTime() {
        buffer.append("сообщение");
        String line = buffer.subscribe(delivered::add).get(0);

        assertTrue("ожидался префикс HH:mm:ss.SSS, получено: " + line,
                line.matches("\\d{2}:\\d{2}:\\d{2}\\.\\d{3}  сообщение"));
    }
}
