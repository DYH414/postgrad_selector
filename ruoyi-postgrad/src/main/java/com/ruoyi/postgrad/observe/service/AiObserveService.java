package com.ruoyi.postgrad.observe.service;

import com.ruoyi.postgrad.observe.domain.AiObserveOverviewVO;
import com.ruoyi.postgrad.observe.tool.domain.AiToolCallTrace;
import java.util.List;

public interface AiObserveService {

    AiObserveOverviewVO getOverview();

    List<AiToolCallTrace> listRecentToolTraces(int limit);

    List<AiToolCallTrace> listRunToolTraces(String runId);
}
