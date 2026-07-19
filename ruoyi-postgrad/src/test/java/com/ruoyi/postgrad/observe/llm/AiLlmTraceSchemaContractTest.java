package com.ruoyi.postgrad.observe.llm;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AiLlmTraceSchemaContractTest {

    @Test
    void runtimeSchemaShouldDefinePersistentLlmTrace() throws Exception {
        Path schema = Path.of("..", "sql", "20_ai_runtime_schema.sql");
        String sql = Files.readString(schema);

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS ai_llm_call_trace"));
        assertTrue(sql.contains("UNIQUE KEY uk_llm_trace_id (trace_id)"));
        assertTrue(sql.contains("KEY idx_llm_run_time (run_id, occurred_at)"));
        assertTrue(sql.contains("KEY idx_llm_occurred (occurred_at)"));
    }
}
