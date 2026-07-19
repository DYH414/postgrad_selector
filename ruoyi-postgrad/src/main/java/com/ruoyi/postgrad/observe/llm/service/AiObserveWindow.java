package com.ruoyi.postgrad.observe.llm.service;

public enum AiObserveWindow {
    HOURS_24("24h", 24),
    DAYS_7("7d", 7 * 24),
    DAYS_30("30d", 30 * 24);

    private final String code;
    private final int hours;

    AiObserveWindow(String code, int hours) {
        this.code = code;
        this.hours = hours;
    }

    public static AiObserveWindow parse(String value) {
        String target = value == null || value.isBlank() ? "24h" : value;
        for (AiObserveWindow window : values()) {
            if (window.code.equals(target)) return window;
        }
        throw new IllegalArgumentException("window 仅支持 24h、7d、30d");
    }

    public String getCode() { return code; }
    public int getHours() { return hours; }
}
