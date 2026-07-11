package com.ruoyi.postgrad.recommend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.postgrad.mapper.RecommendationMapper;
import com.ruoyi.postgrad.rag.dto.RagChunkResult;
import com.ruoyi.postgrad.rag.dto.RagSearchTestRequest;
import com.ruoyi.postgrad.rag.dto.RagSearchTestResponse;
import com.ruoyi.postgrad.rag.service.RagEvidenceService;
import com.ruoyi.postgrad.rag.service.RagSearchTestService;
import com.ruoyi.postgrad.rag.trace.RagTraceRecorder;
import com.ruoyi.postgrad.recommend.domain.AgentRunContext;
import com.ruoyi.postgrad.recommend.service.ChatRagRouteResolver;
import com.ruoyi.postgrad.recommend.service.ChatStreamCallback;
import com.ruoyi.postgrad.recommend.tool.V2BoundChatTools;
import com.ruoyi.postgrad.recommend.tool.V2ChatToolContext;
import com.ruoyi.postgrad.recommend.tool.V2ChatTools;
import dev.langchain4j.rag.RetrievalAugmentor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

class AgentRuntimeFlowContractTest {

    @Test
    void policyAndProgramRiskRunsPreserveRouteEvidenceAndRunId() throws Exception {
        ChatRagRouteResolver resolver = new ChatRagRouteResolver();
        RetrievalAugmentor augmentor = mock(RetrievalAugmentor.class);
        assertTrue(AiChatServiceImpl.usesGenericRetrieval(resolver.resolve("408 考什么科目"), augmentor));

        String riskQuestion = "杭州电子科技大学为什么推荐，有什么风险";
        ChatRagRouteResolver.Route riskRoute = resolver.resolve(riskQuestion);
        assertEquals(ChatRagRouteResolver.Route.SCOPED_EVIDENCE, riskRoute);
        assertFalse(AiChatServiceImpl.usesGenericRetrieval(riskRoute, augmentor));

        AgentRunContext run = AgentRunContext.start(7L);
        RagSearchTestService search = mock(RagSearchTestService.class);
        RagSearchTestResponse searchResponse = new RagSearchTestResponse();
        searchResponse.setRetrievedCount(1);
        searchResponse.setSelectedCount(1);
        searchResponse.setAppliedFilters(Map.of("program_id", "202"));
        searchResponse.setChunks(List.of(selectedRiskChunk()));
        when(search.searchTest(any(RagSearchTestRequest.class))).thenReturn(searchResponse);

        RagTraceRecorder traces = new RagTraceRecorder();
        RagEvidenceService evidence = new RagEvidenceService(search, traces);
        V2ChatTools tools = new V2ChatTools();
        Field field = V2ChatTools.class.getDeclaredField("ragEvidenceService");
        field.setAccessible(true);
        field.set(tools, evidence);
        var toolContext = V2ChatToolContext.init(7L, mock(StringRedisTemplate.class),
            mock(RecommendationMapper.class), run.getRunId());

        String toolResult = new V2BoundChatTools(tools, toolContext)
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
        chunk.setChunkTitle("杭州电子科技大学 电子信息 - 风险因素");
        chunk.setSourceName("数据库生成文档");
        chunk.setSourceUrl("https://example.com/source");
        chunk.setDocType("program_history");
        chunk.setProgramId("202");
        chunk.setSection("risk_factors");
        chunk.setContentPreview("统考名额较少，需要关注历年波动风险。");
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
