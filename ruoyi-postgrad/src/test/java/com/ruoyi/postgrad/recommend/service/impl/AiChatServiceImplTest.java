package com.ruoyi.postgrad.recommend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.ruoyi.postgrad.recommend.domain.DraftAction;

class AiChatServiceImplTest {

    @Test
    void parseDraftActionUsesFirstObjectWhenModelReturnsMultipleActions() {
        DraftAction action = AiChatServiceImpl.parseDraftAction("""
            {"type":"remove","programId":2357,"tier":"steady"}
            {"type":"remove","programId":3082,"tier":"steady"}
            {"type":"replace","programId":42,"tier":"safe","preference":"safer"}
            """);

        assertNotNull(action);
        assertEquals("remove", action.getType());
        assertEquals(2357L, action.getProgramId());
        assertEquals("steady", action.getTier());
    }

    @Test
    void parseDraftActionAcceptsJsonFencedBlock() {
        DraftAction action = AiChatServiceImpl.parseDraftAction("""
            ```json
            {"type":"replace","programId":42,"tier":"safe","preference":"safer"}
            ```
            """);

        assertNotNull(action);
        assertEquals("replace", action.getType());
        assertEquals(42L, action.getProgramId());
        assertEquals("safer", action.getPreference());
    }

    @Test
    void parseDraftActionReturnsNullWhenNoJsonExists() {
        assertNull(AiChatServiceImpl.parseDraftAction("继续分析，不执行操作"));
    }
}
