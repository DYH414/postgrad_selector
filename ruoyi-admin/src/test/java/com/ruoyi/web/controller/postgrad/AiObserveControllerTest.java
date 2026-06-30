package com.ruoyi.web.controller.postgrad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.postgrad.observe.domain.AiObserveOverviewVO;
import com.ruoyi.postgrad.observe.service.AiObserveService;
import org.junit.jupiter.api.Test;

class AiObserveControllerTest {

    @Test
    void overviewShouldWrapServiceResult() {
        AiObserveService service = mock(AiObserveService.class);
        AiObserveOverviewVO overview = new AiObserveOverviewVO();
        overview.setTotalCalls(7L);
        when(service.getOverview()).thenReturn(overview);

        AiObserveController controller = new AiObserveController(service);
        AjaxResult result = controller.overview();

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        assertEquals(7L, ((AiObserveOverviewVO) result.get(AjaxResult.DATA_TAG)).getTotalCalls());
    }
}
