package com.ruoyi.postgrad.recommend.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
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
import com.ruoyi.postgrad.domain.RowMap;
import org.junit.jupiter.api.Test;

class V2ChatToolsRagEvidenceTest {

    @Test
    void getProgramEvidenceDelegatesToRagEvidenceService() throws Exception {
        RagEvidenceService evidenceService = mock(RagEvidenceService.class);
        RecommendationMapper mapper = mock(RecommendationMapper.class);
        RowMap program = new RowMap();
        program.put("schoolName", "广州大学");
        when(mapper.selectProgramForRecommendation(101L)).thenReturn(program);
        RagEvidenceResponse response = new RagEvidenceResponse();
        RagCitation citation = new RagCitation();
        citation.setTitle("广州大学 2025 年硕士研究生招生简章");
        citation.setSchoolName("广州大学");
        citation.setDocType("admission_guide");
        citation.setContentPreview("考生须符合招生简章规定的报考条件。");
        response.setCitations(java.util.List.of(citation));
        when(evidenceService.retrieveProgramEvidence(any(RagEvidenceRequest.class))).thenReturn(response);

        V2ChatTools tools = new V2ChatTools();
        var field = V2ChatTools.class.getDeclaredField("ragEvidenceService");
        field.setAccessible(true);
        field.set(tools, evidenceService);

        V2ChatToolContext.init(7L, mock(StringRedisTemplate.class, RETURNS_DEEP_STUBS), mapper, "run-1");
        try {
            String json = tools.getProgramEvidence(101L, "这个学校的报考条件");
            Map<String, Object> result = JSON.parseObject(json, Map.class);

            assertEquals(true, result.get("ok"));
            assertEquals(101, ((Number) result.get("programId")).intValue());
            assertTrue(json.contains("admission_guide"));
            assertTrue(json.contains("报考条件"));
            ArgumentCaptor<RagEvidenceRequest> captor = ArgumentCaptor.forClass(RagEvidenceRequest.class);
            verify(evidenceService).retrieveProgramEvidence(captor.capture());
            assertEquals("广州大学", captor.getValue().getSchoolName());
            assertEquals("admission_guide", captor.getValue().getDocType());
            assertEquals(null, captor.getValue().getProgramId());
        } finally {
            V2ChatToolContext.clear();
        }
    }

    @Test
    void boundChatToolsExposeProgramEvidenceTool() throws Exception {
        RagEvidenceService evidenceService = mock(RagEvidenceService.class);
        RecommendationMapper mapper = mock(RecommendationMapper.class);
        RowMap program = new RowMap();
        program.put("schoolName", "杭州电子科技大学");
        when(mapper.selectProgramForRecommendation(202L)).thenReturn(program);
        RagEvidenceResponse response = new RagEvidenceResponse();
        RagCitation citation = new RagCitation();
        citation.setTitle("杭州电子科技大学 2025 年硕士研究生招生简章");
        citation.setSchoolName("杭州电子科技大学");
        citation.setDocType("admission_guide");
        citation.setContentPreview("具体招生专业和条件以招生简章为准。");
        response.setCitations(java.util.List.of(citation));
        when(evidenceService.retrieveProgramEvidence(any(RagEvidenceRequest.class))).thenReturn(response);

        V2ChatTools tools = new V2ChatTools();
        var field = V2ChatTools.class.getDeclaredField("ragEvidenceService");
        field.setAccessible(true);
        field.set(tools, evidenceService);

        var context = V2ChatToolContext.init(7L,
            mock(StringRedisTemplate.class, RETURNS_DEEP_STUBS), mapper, "run-1");
        V2BoundChatTools boundTools = new V2BoundChatTools(tools, context,
            new AgentToolRuntime(mock(AiToolTraceService.class)));
        try {
            String json = boundTools.getProgramEvidence(202L, "有哪些报考条件");

            assertTrue(json.contains("admission_guide"));
            assertTrue(json.contains("招生专业和条件"));
            assertTrue(json.contains("run-1"));
            ArgumentCaptor<RagEvidenceRequest> captor = ArgumentCaptor.forClass(RagEvidenceRequest.class);
            verify(evidenceService).retrieveProgramEvidence(captor.capture());
            assertEquals("run-1", captor.getValue().getRunId());
            assertEquals("杭州电子科技大学", captor.getValue().getSchoolName());
        } finally {
            V2ChatToolContext.clear();
        }
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
