package com.ruoyi.web.controller.postgrad;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AiReportConsumerContractTest {
    @Test
    void consumerShouldUseSharedReportBuilder() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/ruoyi/web/controller/postgrad/AiReportConsumer.java"));

        assertTrue(source.contains("AiReportBuilder"));
        assertTrue(source.contains("buildConversationReport"));
        assertTrue(source.contains("buildAnalyzeReport"));
        assertFalse(source.contains("private String buildReportPrompt"));
        assertFalse(source.contains("private void injectFullData"));
    }
}
