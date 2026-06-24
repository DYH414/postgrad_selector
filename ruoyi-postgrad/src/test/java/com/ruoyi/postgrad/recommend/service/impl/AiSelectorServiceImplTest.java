package com.ruoyi.postgrad.recommend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ruoyi.postgrad.recommend.domain.AiSelectionResult;
import com.ruoyi.postgrad.recommend.domain.CandidateCardVO;
import com.ruoyi.postgrad.recommend.domain.SchoolFact;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * TDD: AI 选校降级链路（AI 调用失败的唯一防线）
 *
 * <p>通过标准：</p>
 * <ul>
 *   <li>空候选 → 空结果</li>
 *   <li>候选数 ≤ 档位上限 → 全部选中（带 AI 理由或兜底理由）</li>
 *   <li>LLM 调用异常 → selectAll 降级</li>
 *   <li>AI 返回空/null → selectAll 降级</li>
 *   <li>JSON 解析失败 → selectAll 降级</li>
 *   <li>AI 返回含幻觉的 JSON → validator 拦截幻觉</li>
 *   <li>正常 AI 响应 → 正确解析并返回</li>
 * </ul>
 */
class AiSelectorServiceImplTest {

    private AiSelectorServiceImpl service;
    private ChatModel chatModel;
    private SelectionValidator validator;

    @BeforeEach
    void setUp() throws Exception {
        service = new AiSelectorServiceImpl();
        chatModel = mock(ChatModel.class);
        validator = new SelectionValidator();

        injectField(service, "chatModel", chatModel);
        injectField(service, "validator", validator);

        injectField(service, "reachPromptResource",
            new StringResource("请从候选列表中选择最合适的 3 所冲刺校。只输出 JSON 数组。\n"));
        injectField(service, "steadyPromptResource",
            new StringResource("请从候选列表中选择最合适的 4 所稳妥校。只输出 JSON 数组。\n"));
        injectField(service, "safePromptResource",
            new StringResource("请从候选列表中选择最合适的 3 所保底校。只输出 JSON 数组。\n"));
    }

    private ChatResponse chatResponse(String text) {
        return ChatResponse.builder().aiMessage(AiMessage.from(text)).build();
    }

    // ═══════════ 降级路径 ═══════════

    @Test
    void emptyCandidatesShouldReturnEmptyResult() {
        AiSelectionResult result = service.select("reach", Collections.emptyList(), 300);

        assertEquals("reach", result.getTier());
        assertEquals(0, result.getSelected().size());
    }

    @Test
    void nullCandidatesShouldReturnEmptyResult() {
        AiSelectionResult result = service.select("reach", null, 300);

        assertEquals(0, result.getSelected().size());
    }

    @Test
    void llmFailureShouldFallbackToSelectAll() {
        doThrow(new RuntimeException("API timeout")).when(chatModel).chat(any(ChatMessage[].class));
        List<CandidateCardVO> candidates = buildCandidates(1001L, 1002L, 1003L, 1004L, 1005L);

        AiSelectionResult result = service.select("reach", candidates, 300);

        assertEquals(3, result.getSelected().size(),
            "LLM failure should fallback to top-3 selectAll");
        result.getSelected().forEach(item ->
            assertTrue(item.getReason().contains("匹配度"),
                "fallback items should have auto-generated reason: " + item.getReason()));
    }

    @Test
    void emptyAiResponseShouldFallback() {
        doReturn(chatResponse("")).when(chatModel).chat(any(ChatMessage[].class));
        List<CandidateCardVO> candidates = buildCandidates(1001L, 1002L, 1003L, 1004L, 1005L);

        AiSelectionResult result = service.select("reach", candidates, 300);

        assertEquals(3, result.getSelected().size(),
            "empty AI response should fallback to selectAll");
    }

    @Test
    void garbledJsonShouldFallback() {
        doReturn(chatResponse("你好，我推荐以下学校：\n1. 厦门大学\n2. 福州大学\n这不是 JSON。"))
            .when(chatModel).chat(any(ChatMessage[].class));
        List<CandidateCardVO> candidates = buildCandidates(1001L, 1002L, 1003L, 1004L, 1005L);

        AiSelectionResult result = service.select("reach", candidates, 300);

        assertEquals(3, result.getSelected().size(),
            "unparseable AI response should fallback to selectAll");
    }

    // ═══════════ 候选数 ≤ 上限 ═══════════

    @Test
    void candidatesBelowLimitWithAiOpinionsShouldAllBeSelected() {
        String json = "[{\"programId\":1001,\"reason\":\"推荐\",\"risks\":[],\"pros\":[],\"cons\":[]},"
            + "{\"programId\":1002,\"reason\":\"推荐2\",\"risks\":[],\"pros\":[],\"cons\":[]}]";
        doReturn(chatResponse(json)).when(chatModel).chat(any(ChatMessage[].class));
        List<CandidateCardVO> candidates = buildCandidates(1001L, 1002L);

        AiSelectionResult result = service.select("reach", candidates, 300);

        assertEquals(2, result.getSelected().size(),
            "all candidates should be selected when count ≤ limit");
    }

    @Test
    void aiOpinionFailureForSmallPoolShouldFallbackToSelectAll() {
        doThrow(new RuntimeException("API down")).when(chatModel).chat(any(ChatMessage[].class));
        List<CandidateCardVO> candidates = buildCandidates(1001L, 1002L);

        AiSelectionResult result = service.select("reach", candidates, 300);

        assertEquals(2, result.getSelected().size(),
            "small pool with AI failure should fallback to selectAll");
    }

    // ═══════════ 正常路径 ═══════════

    @Test
    void validAiResponseShouldBeParsedAndValidated() {
        String json = """
            [
                {"programId": 1001, "reason": "分数匹配度高", "risks": ["名额偏少"], "pros": ["985"], "cons": []},
                {"programId": 1002, "reason": "招生人数多", "risks": [], "pros": ["211"], "cons": ["城市偏远"]},
                {"programId": 1003, "reason": "稳妥选择", "risks": [], "pros": [], "cons": []}
            ]""";
        doReturn(chatResponse(json)).when(chatModel).chat(any(ChatMessage[].class));
        List<CandidateCardVO> candidates = buildCandidates(1001L, 1002L, 1003L, 1004L, 1005L);

        AiSelectionResult result = service.select("reach", candidates, 300);

        assertEquals(3, result.getSelected().size());
        assertEquals(0, result.getBlocked().size());
        assertEquals("分数匹配度高", result.getSelected().get(0).getReason());
    }

    @Test
    void aiResponseInMarkdownCodeBlockShouldBeParsed() {
        String json = """
            以下是推荐结果：
            ```json
            [
                {"programId": 1001, "reason": "最佳选择", "risks": [], "pros": [], "cons": []},
                {"programId": 1002, "reason": "次选", "risks": [], "pros": [], "cons": []},
                {"programId": 1003, "reason": "保底", "risks": [], "pros": [], "cons": []}
            ]
            ```
            以上是分析结果。""";
        doReturn(chatResponse(json)).when(chatModel).chat(any(ChatMessage[].class));
        List<CandidateCardVO> candidates = buildCandidates(1001L, 1002L, 1003L, 1004L, 1005L);

        AiSelectionResult result = service.select("reach", candidates, 300);

        assertEquals(3, result.getSelected().size(),
            "JSON in markdown code block should be parsed");
    }

    @Test
    void aiResponseWithHallucinatedIdShouldBeFiltered() {
        String json = """
            [
                {"programId": 1001, "reason": "真实候选", "risks": [], "pros": [], "cons": []},
                {"programId": 9999, "reason": "幻觉候选", "risks": [], "pros": [], "cons": []},
                {"programId": 1002, "reason": "另一个真实", "risks": [], "pros": [], "cons": []}
            ]""";
        doReturn(chatResponse(json)).when(chatModel).chat(any(ChatMessage[].class));
        List<CandidateCardVO> candidates = buildCandidates(1001L, 1002L, 1003L, 1004L, 1005L);

        AiSelectionResult result = service.select("reach", candidates, 300);

        assertEquals(2, result.getSelected().size(),
            "hallucinated programId should be filtered by validator");
        assertEquals(1, result.getBlocked().size());
        assertEquals(9999L, result.getBlocked().get(0).getProgramId());
    }

    // ═══════════ 档位限制 ═══════════

    @Test
    void reachLimitShouldBe3() {
        String json = "[{\"programId\":1001,\"reason\":\"1\",\"risks\":[],\"pros\":[],\"cons\":[]},"
            + "{\"programId\":1002,\"reason\":\"2\",\"risks\":[],\"pros\":[],\"cons\":[]},"
            + "{\"programId\":1003,\"reason\":\"3\",\"risks\":[],\"pros\":[],\"cons\":[]}]";
        doReturn(chatResponse(json)).when(chatModel).chat(any(ChatMessage[].class));
        List<CandidateCardVO> candidates = buildCandidates(1001L, 1002L, 1003L, 1004L, 1005L);

        AiSelectionResult result = service.select("reach", candidates, 300);

        assertEquals(3, result.getSelected().size());
    }

    @Test
    void steadyLimitShouldBe4() {
        String json = "[{\"programId\":1001,\"reason\":\"1\",\"risks\":[],\"pros\":[],\"cons\":[]},"
            + "{\"programId\":1002,\"reason\":\"2\",\"risks\":[],\"pros\":[],\"cons\":[]},"
            + "{\"programId\":1003,\"reason\":\"3\",\"risks\":[],\"pros\":[],\"cons\":[]},"
            + "{\"programId\":1004,\"reason\":\"4\",\"risks\":[],\"pros\":[],\"cons\":[]}]";
        doReturn(chatResponse(json)).when(chatModel).chat(any(ChatMessage[].class));
        List<CandidateCardVO> candidates = buildCandidates(1001L, 1002L, 1003L, 1004L, 1005L);

        AiSelectionResult result = service.select("steady", candidates, 300);

        assertEquals(4, result.getSelected().size());
    }

    // ═══════════ 边界 ═══════════

    @Test
    void candidateWithNullFactShouldBeSkippedWithoutCrash() {
        List<CandidateCardVO> candidates = new ArrayList<>();
        CandidateCardVO nullFact = new CandidateCardVO();
        nullFact.setFact(null);
        candidates.add(nullFact);
        candidates.addAll(buildCandidates(1001L, 1002L, 1003L, 1004L, 1005L));

        String json = "[{\"programId\":1001,\"reason\":\"ok\",\"risks\":[],\"pros\":[],\"cons\":[]},"
            + "{\"programId\":1002,\"reason\":\"ok\",\"risks\":[],\"pros\":[],\"cons\":[]},"
            + "{\"programId\":1003,\"reason\":\"ok\",\"risks\":[],\"pros\":[],\"cons\":[]}]";
        doReturn(chatResponse(json)).when(chatModel).chat(any(ChatMessage[].class));

        AiSelectionResult result = service.select("reach", candidates, 300);

        assertNotNull(result);
        assertEquals(3, result.getSelected().size(),
            "null fact should be skipped without crashing");
    }

    // ═══════════ Helpers ═══════════

    private static List<CandidateCardVO> buildCandidates(Long... programIds) {
        List<CandidateCardVO> list = new ArrayList<>();
        for (int i = 0; i < programIds.length; i++) {
            SchoolFact fact = new SchoolFact();
            fact.setProgramId(programIds[i]);
            fact.setSchoolName("School-" + programIds[i]);
            fact.setProgramName("Program-" + programIds[i]);
            fact.setSchoolTier(i % 3 == 0 ? "985" : "211");
            fact.setCity("City-" + i);
            fact.setAvgAdmittedScore(300 + (i - 2) * 5);
            fact.setScoreGap(i - 2);
            fact.setGapLabel(String.valueOf(i - 2));
            fact.setUnifiedExamQuota(10 + i);
            fact.setDataCompleteness("A");
            fact.setSchoolId(programIds[i]);
            list.add(CandidateCardVO.fromFact(fact));
        }
        return list;
    }

    private static void injectField(Object target, String fieldName, Object value) throws Exception {
        Field field = AiSelectorServiceImpl.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static class StringResource implements org.springframework.core.io.Resource {
        private final String content;
        StringResource(String content) { this.content = content; }
        @Override public java.io.InputStream getInputStream() { return new java.io.ByteArrayInputStream(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
        @Override public boolean exists() { return true; }
        @Override public java.net.URL getURL() { throw new UnsupportedOperationException(); }
        @Override public java.net.URI getURI() { throw new UnsupportedOperationException(); }
        @Override public java.io.File getFile() { throw new UnsupportedOperationException(); }
        @Override public long contentLength() { return content.length(); }
        @Override public long lastModified() { return 0; }
        @Override public org.springframework.core.io.Resource createRelative(String s) { throw new UnsupportedOperationException(); }
        @Override public String getFilename() { return "test-prompt.txt"; }
        @Override public String getDescription() { return "test resource"; }
    }
}
