package com.ruoyi.web.controller.postgrad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.postgrad.observe.domain.AiObserveOverviewVO;
import com.ruoyi.postgrad.observe.service.AiObserveService;
import com.ruoyi.postgrad.observe.tool.domain.AiToolCallTrace;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiObserveControllerTest {

    @Test
    void overviewShouldWrapServiceResult() {
        AiObserveService service = mock(AiObserveService.class);
        AiObserveOverviewVO overview = new AiObserveOverviewVO();
        overview.setTotalCalls(7L);
        when(service.getOverview("24h")).thenReturn(overview);

        AiObserveController controller = new AiObserveController(service);
        AjaxResult result = controller.overview("24h");

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        assertEquals(7L, ((AiObserveOverviewVO) result.get(AjaxResult.DATA_TAG)).getTotalCalls());
    }

    @Test
    void overviewShouldRejectUnsupportedWindow() {
        AiObserveController controller = new AiObserveController(mock(AiObserveService.class));
        AjaxResult result = controller.overview("all");
        assertEquals(400, result.get(AjaxResult.CODE_TAG));
    }

    @Test
    void toolTraceEndpointsShouldWrapRecentAndRunResults() {
        AiObserveService service = mock(AiObserveService.class);
        AiToolCallTrace trace = new AiToolCallTrace();
        trace.setRunId("run-1");
        when(service.listRecentToolTraces(50)).thenReturn(List.of(trace));
        when(service.listRunToolTraces("run-1")).thenReturn(List.of(trace));
        AiObserveController controller = new AiObserveController(service);

        assertEquals(200, controller.toolTraces(50).get(AjaxResult.CODE_TAG));
        assertEquals(200, controller.runToolTraces("run-1").get(AjaxResult.CODE_TAG));
    }
}
