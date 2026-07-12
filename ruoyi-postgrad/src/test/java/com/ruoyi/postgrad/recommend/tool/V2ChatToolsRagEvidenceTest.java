package com.ruoyi.postgrad.recommend.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.ruoyi.postgrad.mapper.RecommendationMapper;
import com.ruoyi.postgrad.observe.tool.service.AiToolTraceService;
import com.ruoyi.postgrad.recommend.tool.runtime.AgentToolRuntime;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.postgrad.rag.dto.RagEvidenceRequest;
import com.ruoyi.postgrad.rag.dto.RagEvidenceResponse;
import com.ruoyi.postgrad.rag.knowledge.RagCitation;
import com.ruoyi.postgrad.rag.service.RagEvidenceService;
import java.util.Map;
import com.ruoyi.postgrad.recommend.domain.SchoolFact;
import org.junit.jupiter.api.Test;

class V2ChatToolsRagEvidenceTest {

    @Test
    void getProgramEvidenceDelegatesToRagEvidenceService() throws Exception {
        RagEvidenceService evidenceService = mock(RagEvidenceService.class);
        RagEvidenceResponse response = new RagEvidenceResponse();
        RagCitation citation = new RagCitation();
        citation.setTitle("广州大学 网络空间安全 - 风险因素");
        citation.setProgramId("101");
        citation.setDocType("program_history");
        citation.setSection("risk_factors");
        citation.setContentPreview("复试线波动较大，需要结合历年趋势判断。");
        response.setCitations(java.util.List.of(citation));
        when(evidenceService.retrieveProgramEvidence(any(RagEvidenceRequest.class))).thenReturn(response);

        V2ChatTools tools = new V2ChatTools();
        var field = V2ChatTools.class.getDeclaredField("ragEvidenceService");
        field.setAccessible(true);
        field.set(tools, evidenceService);

        String json = tools.getProgramEvidence(101L, "这个学校有什么风险");
        Map<String, Object> result = JSON.parseObject(json, Map.class);

        assertEquals(true, result.get("ok"));
        assertEquals(101, ((Number) result.get("programId")).intValue());
        assertTrue(json.contains("risk_factors"));
        assertTrue(json.contains("复试线波动"));
    }

    @Test
    void boundChatToolsExposeProgramEvidenceTool() throws Exception {
        RagEvidenceService evidenceService = mock(RagEvidenceService.class);
        RagEvidenceResponse response = new RagEvidenceResponse();
        RagCitation citation = new RagCitation();
        citation.setTitle("杭州电子科技大学 电子信息 - 风险因素");
        citation.setProgramId("202");
        citation.setDocType("program_history");
        citation.setSection("risk_factors");
        citation.setContentPreview("统考名额较少，需要关注波动风险。");
        response.setCitations(java.util.List.of(citation));
        when(evidenceService.retrieveProgramEvidence(any(RagEvidenceRequest.class))).thenReturn(response);

        V2ChatTools tools = new V2ChatTools();
        var field = V2ChatTools.class.getDeclaredField("ragEvidenceService");
        field.setAccessible(true);
        field.set(tools, evidenceService);

        var context = V2ChatToolContext.init(7L, mock(StringRedisTemplate.class),
            mock(RecommendationMapper.class), "run-1");
        V2BoundChatTools boundTools = new V2BoundChatTools(tools, context,
            new AgentToolRuntime(mock(AiToolTraceService.class)));
        String json = boundTools.getProgramEvidence(202L, "为什么推荐，有什么风险");

        assertTrue(json.contains("risk_factors"));
        assertTrue(json.contains("统考名额较少"));
        assertTrue(json.contains("run-1"));
        ArgumentCaptor<RagEvidenceRequest> captor = ArgumentCaptor.forClass(RagEvidenceRequest.class);
        verify(evidenceService).retrieveProgramEvidence(captor.capture());
        assertEquals("run-1", captor.getValue().getRunId());
    }

    @Test
    void searchProgramResultShouldExposeRecruitmentUnitAndDataYear() throws Exception {
        SchoolFact fact = new SchoolFact();
        fact.setProgramId(3122L);
        fact.setSchoolName("深圳大学");
        fact.setCollegeName("教育学部");
        fact.setProgramName("电子信息-人工智能");
        fact.setProgramCode("085410");
        fact.setDegreeType("professional");
        fact.setDataYear(2025);

        V2ChatTools tools = new V2ChatTools();
        var method = V2ChatTools.class.getDeclaredMethod("toSearchItem", SchoolFact.class);
        method.setAccessible(true);
        Map<String, Object> result = (Map<String, Object>) method.invoke(tools, fact);

        assertEquals("教育学部", result.get("collegeName"));
        assertEquals("085410", result.get("programCode"));
        assertEquals("professional", result.get("degreeType"));
        assertEquals(2025, result.get("dataYear"));
        assertEquals("电子信息-人工智能（教育学部，085410，2025）", result.get("displayName"));
    }
}
