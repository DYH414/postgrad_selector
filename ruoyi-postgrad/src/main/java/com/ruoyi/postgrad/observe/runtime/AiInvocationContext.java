package com.ruoyi.postgrad.observe.runtime;

public final class AiInvocationContext {

    private static final ThreadLocal<String> OPERATION_KEY = new ThreadLocal<>();

    private AiInvocationContext() {
    }

    public static void setOperationKey(String operationKey) {
        OPERATION_KEY.set(operationKey);
    }

    public static String getOperationKey() {
        String value = OPERATION_KEY.get();
        return value == null || value.isBlank() ? "unknown" : value;
    }

    public static void clear() {
        OPERATION_KEY.remove();
    }
}
