package com.ruoyi.postgrad.rag.eval.workbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.postgrad.rag.eval.workbench.domain.AgentConfigVersion;
import com.ruoyi.postgrad.rag.eval.workbench.mapper.RagEvaluationWorkbenchMapper;
import com.ruoyi.postgrad.rag.eval.workbench.service.AgentConfigVersionService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AgentConfigVersionServiceTest {

    private static final String MODEL = "deepseek-v4-flash";
    private static final double TEMP = 0.7;

    private AgentConfigVersionService service(RagEvaluationWorkbenchMapper mapper) {
        return new AgentConfigVersionService(mapper, MODEL, TEMP, "BAAI/bge-m3", "BAAI/bge-reranker-v2-m3");
    }

    private AgentConfigVersion request(String model, BigDecimal temp, String status) {
        AgentConfigVersion v = new AgentConfigVersion();
        v.setVersionName("prompt-v2"); v.setBusinessPromptText("业务提示词"); v.setRoutingPromptText("路由提示词");
        v.setModel(model); v.setTemperature(temp); v.setStatus(status);
        return v;
    }

    @Test
    void activeVersionMatchingRuntimeIsCreatedAndChecksummedServerSide() {
        RagEvaluationWorkbenchMapper mapper = mock(RagEvaluationWorkbenchMapper.class);
        doAnswer(inv -> { ((AgentConfigVersion) inv.getArgument(0)).setId(1L); return 1; })
            .when(mapper).insertAgentConfigVersion(any());
        when(mapper.selectAgentConfigVersionById(1L)).thenAnswer(inv -> {
            AgentConfigVersion v = request(MODEL, new BigDecimal("0.700"), "ACTIVE"); v.setId(1L); return v;
        });

        AgentConfigVersion req = request(MODEL, new BigDecimal("0.700"), "ACTIVE");
        req.setBusinessPromptChecksum("client-forged"); // 应被服务端忽略
        service(mapper).create(req, "admin");

        verify(mapper).insertAgentConfigVersion(any());
        assertNotNull(req.getBusinessPromptChecksum());
        assertNotEquals("client-forged", req.getBusinessPromptChecksum()); // 服务端重算
        assertEquals(64, req.getBusinessPromptChecksum().length());
        assertNotNull(req.getToolCatalogChecksum());
        assertNotNull(req.getRagConfigSnapshotJson());
    }

    @Test
    void activeVersionWithMismatchedModelIsRejected() {
        RagEvaluationWorkbenchMapper mapper = mock(RagEvaluationWorkbenchMapper.class);
        AgentConfigVersion req = request("gpt-4o", new BigDecimal("0.700"), "ACTIVE");

        ServiceException ex = assertThrows(ServiceException.class, () -> service(mapper).create(req, "admin"));
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("AGENT_CONFIG_RUNTIME_MISMATCH"));
        verify(mapper, never()).insertAgentConfigVersion(any());
    }

    @Test
    void activeVersionWithMismatchedTemperatureIsRejected() {
        RagEvaluationWorkbenchMapper mapper = mock(RagEvaluationWorkbenchMapper.class);
        AgentConfigVersion req = request(MODEL, new BigDecimal("0.500"), "ACTIVE");

        assertThrows(ServiceException.class, () -> service(mapper).create(req, "admin"));
        verify(mapper, never()).insertAgentConfigVersion(any());
    }

    @Test
    void archivedVersionSkipsRuntimeMatchCheck() {
        RagEvaluationWorkbenchMapper mapper = mock(RagEvaluationWorkbenchMapper.class);
        doAnswer(inv -> { ((AgentConfigVersion) inv.getArgument(0)).setId(2L); return 1; })
            .when(mapper).insertAgentConfigVersion(any());
        when(mapper.selectAgentConfigVersionById(2L)).thenReturn(request("any-model", new BigDecimal("1.500"), "ARCHIVED"));

        // ARCHIVED 不校验运行时一致 → 即便 model/temperature 不同也能建
        service(mapper).create(request("any-model", new BigDecimal("1.500"), "ARCHIVED"), "admin");
        verify(mapper).insertAgentConfigVersion(any());
    }
}
