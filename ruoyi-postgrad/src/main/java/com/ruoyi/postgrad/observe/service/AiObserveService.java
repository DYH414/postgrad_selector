package com.ruoyi.postgrad.observe.service;

import com.ruoyi.postgrad.observe.domain.AiObserveOverviewVO;
import com.ruoyi.postgrad.observe.llm.domain.AiLlmCallTrace;
import com.ruoyi.postgrad.observe.tool.domain.AiToolCallTrace;
import java.util.List;

public interface AiObserveService {
    default AiObserveOverviewVO getOverview() { return getOverview("24h"); }
    AiObserveOverviewVO getOverview(String window);
    List<AiToolCallTrace> listRecentToolTraces(int limit);
    List<AiToolCallTrace> listRunToolTraces(String runId);
    List<AiLlmCallTrace> listRunLlmTraces(String runId);
}
