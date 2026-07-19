package com.ruoyi.postgrad.observe.llm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AiObserveWindowTest {

    @Test
    void shouldSupportOnlyDocumentedWindows() {
        assertEquals(24, AiObserveWindow.parse("24h").getHours());
        assertEquals(168, AiObserveWindow.parse("7d").getHours());
        assertEquals(720, AiObserveWindow.parse("30d").getHours());
        assertThrows(IllegalArgumentException.class, () -> AiObserveWindow.parse("all"));
    }
}
