package com.ruoyi.postgrad.recommend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.postgrad.mapper.RecommendationMapper;
import com.ruoyi.postgrad.domain.RowMap;
import com.ruoyi.postgrad.observe.tool.service.AiToolTraceService;
import com.ruoyi.postgrad.rag.dto.RagChunkResult;
import com.ruoyi.postgrad.rag.dto.RagSearchTestRequest;
import com.ruoyi.postgrad.rag.dto.RagSearchTestResponse;
import com.ruoyi.postgrad.rag.service.RagEvidenceService;
import com.ruoyi.postgrad.rag.service.RagRetrievalResult;
import com.ruoyi.postgrad.rag.service.RagRetrievalService;
import com.ruoyi.postgrad.rag.trace.RagTraceRecorder;
import com.ruoyi.postgrad.recommend.domain.AgentRunContext;
import com.ruoyi.postgrad.recommend.service.ChatStreamCallback;
import com.ruoyi.postgrad.recommend.tool.V2BoundChatTools;
import com.ruoyi.postgrad.recommend.tool.V2ChatToolContext;
import com.ruoyi.postgrad.recommend.tool.V2ChatTools;
import com.ruoyi.postgrad.recommend.tool.runtime.AgentToolRuntime;
import dev.langchain4j.rag.RetrievalAugmentor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class AgentRuntimeFlowContractTest {

    @Test
    void chatPromptGuardsContinuationEvidenceAndScoreGapWording() throws Exception {
        String path = "/prompts/v2/chat-system.txt";
        try (var stream = AgentRuntimeFlowContractTest.class.getResourceAsStream(path)) {
            assertTrue(stream != null, "chat system prompt should exist");
            String prompt = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(prompt.contains("当前草稿快照是唯一事实来源"));
            assertTrue(prompt.contains("“继续”“可以”不等于界面确认"));
            assertTrue(prompt.contains("不得重复生成同一提案"));
            assertTrue(prompt.contains("不能声称系统不支持该能力"));
            assertTrue(prompt.contains("预估分 - 录取均分"));
            assertTrue(prompt.contains("不得为了凑数量编造风险"));
            assertTrue(prompt.contains("草稿写入意图不得只生成操作说明"));
            assertTrue(prompt.contains("必须调用本轮提供的对应写工具"));
            assertTrue(prompt.contains("没有收到 confirmationRequired=true"));
        }
    }

    @Test
    void policyAndProgramRiskRunsPreserveRouteEvidenceAndRunId() throws Exception {
        RetrievalAugmentor augmentor = mock(RetrievalAugmentor.class);
        assertFalse(AiChatServiceImpl.usesGenericRetrieval("408 考什么科目", augmentor));
        assertFalse(AiChatServiceImpl.usesGenericRetrieval("杭州电子科技大学为什么推荐，有什么风险", augmentor));

        String riskQuestion = "杭州电子科技大学为什么推荐，有什么风险";
        AgentRunContext run = AgentRunContext.start(7L);
        RagRetrievalService retrieval = mock(RagRetrievalService.class);
        RagSearchTestResponse searchResponse = new RagSearchTestResponse();
        searchResponse.setRetrievedCount(1);
        searchResponse.setSelectedCount(1);
        searchResponse.setAppliedFilters(Map.of(
            "school_name", "杭州电子科技大学",
            "doc_type", "admission_guide"));
        searchResponse.setChunks(List.of(selectedRiskChunk()));
        when(retrieval.retrieve(any(RagSearchTestRequest.class)))
            .thenReturn(new RagRetrievalResult(searchResponse, List.of()));

        RagTraceRecorder traces = new RagTraceRecorder();
        RagEvidenceService evidence = new RagEvidenceService(retrieval, traces);
        V2ChatTools tools = new V2ChatTools();
        Field field = V2ChatTools.class.getDeclaredField("ragEvidenceService");
        field.setAccessible(true);
        field.set(tools, evidence);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        RecommendationMapper mapper = mock(RecommendationMapper.class);
        RowMap program = new RowMap();
        program.put("schoolName", "杭州电子科技大学");
        when(mapper.selectProgramForRecommendation(202L)).thenReturn(program);
        var toolContext = V2ChatToolContext.init(7L, redis, mapper, run.getRunId());

        String toolResult = new V2BoundChatTools(tools, toolContext,
            new AgentToolRuntime(mock(AiToolTraceService.class)))
            .getProgramEvidence(202L, riskQuestion);
        Map<String, Object> parsed = JSON.parseObject(toolResult);
        assertEquals(run.getRunId(), parsed.get("runId"));
        assertFalse(((List<?>) parsed.get("citations")).isEmpty());
        assertEquals(run.getRunId(), traces.listRecent(1).get(0).getRunId());
        assertEquals("scoped-evidence", traces.listRecent(1).get(0).getRetrievalRoute());

        RecordingCallback callback = new RecordingCallback();
        callback.onRunStarted(run.getRunId());
        callback.onToolCall("getProgramEvidence", run.getRunId());
        callback.onDone("有证据的风险说明", false, toolResult, run.getRunId());
        assertEquals(run.getRunId(), callback.startedRunId);
        assertEquals(run.getRunId(), callback.toolRunId);
        assertEquals(run.getRunId(), callback.terminalRunId);
        assertEquals("getProgramEvidence", callback.toolName);
    }

    private RagChunkResult selectedRiskChunk() {
        RagChunkResult chunk = new RagChunkResult();
        chunk.setChunkTitle("杭州电子科技大学 2025 年硕士研究生招生简章");
        chunk.setSourceName("杭州电子科技大学-招生简章.md");
        chunk.setSourceUrl("https://example.com/source");
        chunk.setDocType("admission_guide");
        chunk.setSchoolName("杭州电子科技大学");
        chunk.setSection("application_requirements");
        chunk.setContentPreview("考生须符合招生简章中的报考条件。");
        chunk.setSelectedForPrompt(true);
        return chunk;
    }

    private static final class RecordingCallback implements ChatStreamCallback {
        private String startedRunId;
        private String toolRunId;
        private String terminalRunId;
        private String toolName;

        @Override public void onRunStarted(String runId) { this.startedRunId = runId; }
        @Override public void onToolCall(String name, String runId) {
            this.toolName = name;
            this.toolRunId = runId;
        }
        @Override public void onDone(String message, boolean changed, String result, String runId) {
            this.terminalRunId = runId;
        }
        @Override public void onToken(String token) { }
        @Override public void onError(Throwable error) { }
    }
}
