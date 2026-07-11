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

import com.alibaba.fastjson2.JSON;
import com.ruoyi.postgrad.rag.dto.RagEvidenceRequest;
import com.ruoyi.postgrad.rag.dto.RagEvidenceResponse;
import com.ruoyi.postgrad.rag.knowledge.RagCitation;
import com.ruoyi.postgrad.rag.service.RagEvidenceService;
import java.util.Map;
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
        V2BoundChatTools boundTools = new V2BoundChatTools(tools, context);
        String json = boundTools.getProgramEvidence(202L, "为什么推荐，有什么风险");

        assertTrue(json.contains("risk_factors"));
        assertTrue(json.contains("统考名额较少"));
        assertTrue(json.contains("run-1"));
        ArgumentCaptor<RagEvidenceRequest> captor = ArgumentCaptor.forClass(RagEvidenceRequest.class);
        verify(evidenceService).retrieveProgramEvidence(captor.capture());
        assertEquals("run-1", captor.getValue().getRunId());
    }
}
