package com.ruoyi.postgrad.recommend.tool.runtime;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

/** Normalizes String-returning business tools into a stable Agent-facing protocol. */
public final class AgentToolResult {

    private AgentToolResult() {
    }

    public static String normalize(String raw, String callId, String runId, String toolName) {
        JSONObject normalized = base(callId, runId, toolName);
        if (raw == null || raw.isBlank()) {
            normalized.put("ok", true);
            normalized.put("code", "NO_DATA");
            return normalized.toJSONString();
        }
        try {
            Object parsed = JSON.parse(raw);
            if (parsed instanceof JSONObject object && object.containsKey("ok")) {
                normalized.putAll(object);
                normalized.remove("confirmationToken");
                normalized.put("callId", callId);
                normalized.put("runId", runId);
                normalized.put("tool", toolName);
                if (normalized.getBooleanValue("confirmationRequired")) {
                    normalized.put("code", "WAITING_CONFIRMATION");
                } else if (!normalized.containsKey("code")) {
                    normalized.put("code", normalized.getBooleanValue("ok") ? "OK" : "TOOL_FAILED");
                }
            } else if (parsed instanceof JSONArray array) {
                normalized.put("ok", true);
                normalized.put("code", array.isEmpty() ? "NO_DATA" : "OK");
                normalized.put("data", array);
            } else {
                normalized.put("ok", true);
                normalized.put("code", "OK");
                normalized.put("data", parsed);
            }
        } catch (RuntimeException ignored) {
            normalized.put("ok", true);
            normalized.put("code", "OK");
            normalized.put("data", raw);
        }
        return normalized.toJSONString();
    }

    public static String error(String code, String message, boolean retryable,
        String callId, String runId, String toolName) {
        JSONObject result = base(callId, runId, toolName);
        result.put("ok", false);
        result.put("code", code);
        result.put("message", message);
        result.put("retryable", retryable);
        return result.toJSONString();
    }

    public static Summary summarize(String normalizedJson) {
        JSONObject value = JSON.parseObject(normalizedJson);
        boolean confirmationRequired = value.getBooleanValue("confirmationRequired");
        String status = confirmationRequired ? "WAITING_CONFIRMATION"
            : value.getBooleanValue("ok") ? "SUCCEEDED" : "FAILED";
        String code = value.getString("code");
        String summary = firstText(value, "summary", "message", "data");
        if (summary == null) {
            summary = code;
        }
        summary = truncate(summary, 1000);
        return new Summary(status, code, value.getBooleanValue("retryable"), confirmationRequired,
            summary, (int) Math.ceil(summary.length() / 2.0));
    }

    private static JSONObject base(String callId, String runId, String toolName) {
        JSONObject value = new JSONObject();
        value.put("callId", callId);
        value.put("runId", runId);
        value.put("tool", toolName);
        return value;
    }

    private static String firstText(JSONObject value, String... keys) {
        for (String key : keys) {
            Object field = value.get(key);
            if (field != null) {
                return String.valueOf(field);
            }
        }
        return null;
    }

    private static String truncate(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit);
    }

    public record Summary(String status, String code, boolean retryable,
        boolean confirmationRequired, String resultSummary, int tokenEstimate) {
    }
}
