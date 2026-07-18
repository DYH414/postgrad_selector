package com.ruoyi.postgrad.rag.eval.workbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.postgrad.rag.eval.workbench.controller.RagEvaluationWorkbenchController;
import com.ruoyi.postgrad.rag.eval.workbench.domain.RagEvalDataset;
import com.ruoyi.postgrad.rag.eval.workbench.mapper.RagEvaluationWorkbenchMapper;
import com.ruoyi.postgrad.rag.eval.workbench.service.RagEvalDatasetService;
import com.ruoyi.postgrad.rag.eval.workbench.service.RagEvalRunService;
import com.ruoyi.postgrad.rag.mapper.RagRetrievalTraceMapper;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class RagEvaluationWorkbenchControllerTest {
    @AfterEach void clearRequest() { RequestContextHolder.resetRequestAttributes(); }

    @Test
    void datasetListingUsesRuoYiPaginationContract() {
        RagEvalDatasetService datasets = mock(RagEvalDatasetService.class);
        RagEvalDataset dataset = new RagEvalDataset(); dataset.setId(1L);
        when(datasets.list(null, null)).thenReturn(List.of(dataset));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("pageNum", "1"); request.setParameter("pageSize", "20");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        TableDataInfo result = controller(datasets, mock(RagEvalRunService.class),
            mock(RagRetrievalTraceMapper.class)).datasets(null, null);

        assertEquals(1, result.getRows().size());
        assertEquals(200, result.getCode());
    }

    @Test
    void missingTraceUsesStableAjaxErrorContract() {
        RagRetrievalTraceMapper traces = mock(RagRetrievalTraceMapper.class);
        AjaxResult result = controller(mock(RagEvalDatasetService.class), mock(RagEvalRunService.class), traces)
            .traceDraft(99L);
        assertEquals(500, result.get(AjaxResult.CODE_TAG));
        assertEquals("RAG trace not found", result.get(AjaxResult.MSG_TAG));
    }

    @Test
    void runListingPassesAllFiltersToService() {
        RagEvalRunService runs = mock(RagEvalRunService.class);
        when(runs.list(7L, "SUCCEEDED", "abc123", "checksum-v1")).thenReturn(List.of());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("pageNum", "2");
        request.setParameter("pageSize", "10");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        controller(mock(RagEvalDatasetService.class), runs, mock(RagRetrievalTraceMapper.class))
            .listRuns(7L, "SUCCEEDED", "abc123", "checksum-v1");

        verify(runs).list(7L, "SUCCEEDED", "abc123", "checksum-v1");
    }

    @Test
    void everyWorkbenchOperationDeclaresPermission() {
        for (Method method : RagEvaluationWorkbenchController.class.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isPublic(method.getModifiers())
                && !method.getDeclaringClass().equals(Object.class)
                && !method.getName().equals("getUsername")) {
                assertNotNull(method.getAnnotation(PreAuthorize.class), method.getName());
            }
        }
    }

    private RagEvaluationWorkbenchController controller(RagEvalDatasetService datasets, RagEvalRunService runs,
                                                         RagRetrievalTraceMapper traces) {
        return new RagEvaluationWorkbenchController(datasets, runs,
            mock(RagEvaluationWorkbenchMapper.class), traces);
    }
}
