package com.wt.airconditioner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Разбор системного списка включённых служб доступности. Проверяется тестом,
 * потому что на голове ошибка тихая: экран покажет «автозапуск включён» при
 * выключенной службе, и после сна подогрев просто не поднимется.
 */
public class AccessibilityToggleTest {

    private static final String SELF =
            "com.wt.airconditioner/com.wt.airconditioner.HeatAccessibilityService";

    @Test
    public void findsServiceAmongOthers() {
        assertTrue(AccessibilityToggle.contains(
                "com.other/com.other.Service:" + SELF, SELF));
    }

    @Test
    public void findsServiceWrittenInShortForm() {
        assertTrue(AccessibilityToggle.contains(
                "com.wt.airconditioner/.HeatAccessibilityService", SELF));
    }

    @Test
    public void doesNotConfuseOtherServiceOfSamePackage() {
        assertFalse(AccessibilityToggle.contains(
                "com.wt.airconditioner/.SomeOtherService", SELF));
    }

    @Test
    public void treatsEmptyListAsDisabled() {
        assertFalse(AccessibilityToggle.contains(null, SELF));
        assertFalse(AccessibilityToggle.contains("", SELF));
    }

    @Test
    public void appendsWithoutLosingOrDuplicating() {
        assertEquals("com.other/com.other.Service:" + SELF,
                AccessibilityToggle.appended("com.other/com.other.Service", SELF));
        assertEquals(SELF, AccessibilityToggle.appended("", SELF));
        assertEquals(SELF, AccessibilityToggle.appended(SELF, SELF));
    }
}
