package com.ruoyi.postgrad.recommend.tool.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

class AgentToolResultTest {

    @Test
    void normalizesEmptyArrayAsNoData() {
        JSONObject value = JSON.parseObject(
            AgentToolResult.normalize("[]", "call-1", "run-1", "searchPrograms"));

        assertTrue(value.getBooleanValue("ok"));
        assertEquals("NO_DATA", value.getString("code"));
    }

    @Test
    void preservesConfirmationWithoutInventingToken() {
        String raw = "{\"ok\":true,\"confirmationRequired\":true,\"summary\":\"replace\"}";
        JSONObject value = JSON.parseObject(
            AgentToolResult.normalize(raw, "call-1", "run-1", "replaceDraftCandidate"));

        assertEquals("WAITING_CONFIRMATION", value.getString("code"));
        assertTrue(value.getBooleanValue("confirmationRequired"));
        assertFalse(value.containsKey("confirmationToken"));
    }
}
