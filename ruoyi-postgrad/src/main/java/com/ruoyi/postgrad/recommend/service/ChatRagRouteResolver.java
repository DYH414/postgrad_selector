package com.ruoyi.postgrad.recommend.service;

import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class ChatRagRouteResolver {

    public enum Route {
        GENERAL("general"),
        SCOPED_EVIDENCE("scoped-evidence");

        private final String traceValue;

        Route(String traceValue) {
            this.traceValue = traceValue;
        }

        public String traceValue() {
            return traceValue;
        }
    }

    public Route resolve(String message) {
        String text = message == null ? "" : message.toLowerCase(Locale.ROOT);
        return containsAny(text, "为什么推荐", "有什么风险", "录取分", "分数线", "招生人数", "招生计划", "录取结果", "历年趋势")
            ? Route.SCOPED_EVIDENCE : Route.GENERAL;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }
}
