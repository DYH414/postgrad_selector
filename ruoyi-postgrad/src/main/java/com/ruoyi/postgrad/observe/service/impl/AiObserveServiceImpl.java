package com.ruoyi.postgrad.observe.service.impl;

import com.ruoyi.postgrad.observe.domain.AiObserveGroupVO;
import com.ruoyi.postgrad.observe.domain.AiObserveLatestVO;
import com.ruoyi.postgrad.observe.domain.AiObserveOverviewVO;
import com.ruoyi.postgrad.observe.domain.AiObserveRealtimeVO;
import com.ruoyi.postgrad.observe.domain.RagQuickStatusVO;
import com.ruoyi.postgrad.observe.runtime.AiInvocationSnapshot;
import com.ruoyi.postgrad.observe.runtime.AiInvocationSnapshotRecorder;
import com.ruoyi.postgrad.observe.service.AiObserveService;
import com.ruoyi.postgrad.observe.llm.domain.AiLlmCallTrace;
import com.ruoyi.postgrad.observe.llm.service.AiLlmObserveSnapshot;
import com.ruoyi.postgrad.observe.llm.service.AiLlmTraceQueryService;
import com.ruoyi.postgrad.observe.llm.service.AiObserveWindow;
import com.ruoyi.postgrad.observe.tool.domain.AiToolCallTrace;
import com.ruoyi.postgrad.observe.tool.service.AiToolTraceService;
import com.ruoyi.postgrad.rag.RagStatus;
import com.ruoyi.postgrad.rag.status.RagIndexStatusService;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class AiObserveServiceImpl implements AiObserveService {

    private static final String TOKEN_USAGE = "gen_ai.client.token.usage";
    private static final String OPERATION_DURATION = "gen_ai.client.operation.duration";

    private final MeterRegistry meterRegistry;
    private final RagIndexStatusService ragIndexStatusService;
    private final AiInvocationSnapshotRecorder snapshotRecorder;
    private final AiToolTraceService toolTraceService;
    private final AiLlmTraceQueryService llmTraceQueryService;

    public AiObserveServiceImpl(MeterRegistry meterRegistry,
                                RagIndexStatusService ragIndexStatusService,
                                AiInvocationSnapshotRecorder snapshotRecorder) {
        this(meterRegistry, ragIndexStatusService, snapshotRecorder, null, null);
    }

    public AiObserveServiceImpl(MeterRegistry meterRegistry,
                                RagIndexStatusService ragIndexStatusService,
                                AiInvocationSnapshotRecorder snapshotRecorder,
                                AiToolTraceService toolTraceService) {
        this(meterRegistry, ragIndexStatusService, snapshotRecorder, toolTraceService, null);
    }

    @Autowired
    public AiObserveServiceImpl(MeterRegistry meterRegistry,
                                RagIndexStatusService ragIndexStatusService,
                                AiInvocationSnapshotRecorder snapshotRecorder,
                                AiToolTraceService toolTraceService,
                                AiLlmTraceQueryService llmTraceQueryService) {
        this.meterRegistry = meterRegistry;
        this.ragIndexStatusService = ragIndexStatusService;
        this.snapshotRecorder = snapshotRecorder;
        this.toolTraceService = toolTraceService;
        this.llmTraceQueryService = llmTraceQueryService;
    }

    @Override
    public AiObserveOverviewVO getOverview(String windowValue) {
        AiObserveWindow window = AiObserveWindow.parse(windowValue);
        AiObserveOverviewVO overview = new AiObserveOverviewVO();
        overview.setWindow(window.getCode());
        AiObserveRealtimeVO realtime = realtimeOverview();
        overview.setRealtime(realtime);
        if (llmTraceQueryService == null) applyRealtimeAsHistory(overview, realtime);
        else applyPersistedHistory(overview, llmTraceQueryService.load(window));
        overview.setRag(toRag(ragIndexStatusService.getStatus()));
        return overview;
    }

    private AiObserveRealtimeVO realtimeOverview() {
        AiObserveRealtimeVO realtime = new AiObserveRealtimeVO();
        realtime.setInputTokens(totalTokenUsage("input"));
        realtime.setOutputTokens(totalTokenUsage("output"));
        realtime.setAverageDurationMs(averageDurationMs());
        realtime.setTotalCalls(totalCalls());
        realtime.setSuccessRate(successRate(realtime.getTotalCalls()));
        return realtime;
    }

    private void applyRealtimeAsHistory(AiObserveOverviewVO overview, AiObserveRealtimeVO realtime) {
        overview.setInputTokens(realtime.getInputTokens());
        overview.setOutputTokens(realtime.getOutputTokens());
        overview.setAverageDurationMs(realtime.getAverageDurationMs());
        overview.setTotalCalls(realtime.getTotalCalls());
        overview.setSuccessRate(realtime.getSuccessRate());
        overview.setModelGroups(groupByTag("gen_ai.request.model"));
        overview.setOperationGroups(groupByTag("postgrad.ai.operation"));
        overview.setLatest(toLatest(snapshotRecorder.getLatest()));
    }

    private void applyPersistedHistory(AiObserveOverviewVO overview, AiLlmObserveSnapshot snapshot) {
        overview.setWindowStart(snapshot.getWindowStart().toString());
        overview.setTotalCalls(snapshot.getAggregate().getTotalCalls());
        overview.setSuccessRate(snapshot.getAggregate().successRate());
        overview.setAverageDurationMs(snapshot.getAggregate().getAverageDurationMs());
        overview.setInputTokens(snapshot.getAggregate().getInputTokens());
        overview.setOutputTokens(snapshot.getAggregate().getOutputTokens());
        overview.setModelGroups(snapshot.getModelGroups());
        overview.setOperationGroups(snapshot.getOperationGroups());
        overview.setLatest(toLatest(snapshot.getLatest()));
    }

    @Override
    public List<AiToolCallTrace> listRecentToolTraces(int limit) {
        return toolTraceService == null ? List.of() : toolTraceService.listRecent(limit);
    }

    @Override
    public List<AiToolCallTrace> listRunToolTraces(String runId) {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId is required");
        }
        return toolTraceService == null ? List.of() : toolTraceService.listByRunId(runId, 100);
    }

    @Override
    public List<AiLlmCallTrace> listRunLlmTraces(String runId) {
        return llmTraceQueryService == null ? List.of() : llmTraceQueryService.listByRunId(runId, 100);
    }

    private double totalTokenUsage(String tokenType) {
        return meterRegistry.find(TOKEN_USAGE)
            .tag("gen_ai.token.type", tokenType)
            .summaries()
            .stream()
            .mapToDouble(DistributionSummary::totalAmount)
            .sum();
    }

    private double averageDurationMs() {
        Collection<Timer> timers = meterRegistry.find(OPERATION_DURATION).timers();
        double totalMs = timers.stream().mapToDouble(timer -> timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).sum();
        long count = timers.stream().mapToLong(Timer::count).sum();
        return count == 0 ? 0.0D : totalMs / count;
    }

    private long totalCalls() {
        return meterRegistry.find(OPERATION_DURATION).timers()
            .stream()
            .mapToLong(Timer::count)
            .sum();
    }

    private double successRate(long totalCalls) {
        if (totalCalls == 0) {
            return 0.0D;
        }
        long successCalls = meterRegistry.find(OPERATION_DURATION)
            .tag("outcome", "SUCCESS")
            .timers()
            .stream()
            .mapToLong(Timer::count)
            .sum();
        return (successCalls * 100.0D) / totalCalls;
    }

    private List<AiObserveGroupVO> groupByTag(String tagKey) {
        Map<String, AiObserveGroupVO> groups = new LinkedHashMap<>();
        meterRegistry.find(OPERATION_DURATION).timers().forEach(timer -> {
            String tagValue = timer.getId().getTag(tagKey);
            if (tagValue == null || tagValue.isBlank()) {
                tagValue = "unknown";
            }
            final String groupKey = tagValue;
            AiObserveGroupVO group = groups.computeIfAbsent(groupKey, ignored -> {
                AiObserveGroupVO item = new AiObserveGroupVO();
                item.setKey(groupKey);
                return item;
            });
            group.setCalls(group.getCalls() + timer.count());
        });
        return new ArrayList<>(groups.values());
    }

    private AiObserveLatestVO toLatest(AiInvocationSnapshot snapshot) {
        AiObserveLatestVO latest = new AiObserveLatestVO();
        if (snapshot == null) {
            return latest;
        }
        latest.setOperationKey(snapshot.getOperationKey());
        latest.setRunId(snapshot.getRunId());
        latest.setProvider(snapshot.getProvider());
        latest.setModel(snapshot.getModel());
        latest.setOutcome(snapshot.getOutcome());
        latest.setErrorSummary(snapshot.getErrorSummary());
        latest.setDurationMs(snapshot.getDurationMs());
        latest.setInputTokens(snapshot.getInputTokens());
        latest.setOutputTokens(snapshot.getOutputTokens());
        latest.setOccurredAt(snapshot.getOccurredAt() != null ? snapshot.getOccurredAt().toString() : null);
        return latest;
    }

    private AiObserveLatestVO toLatest(AiLlmCallTrace trace) {
        AiObserveLatestVO latest = new AiObserveLatestVO();
        if (trace == null) return latest;
        latest.setOperationKey(trace.getOperationKey());
        latest.setRunId(trace.getRunId());
        latest.setProvider(trace.getProvider());
        latest.setModel(trace.getModel());
        latest.setOutcome(trace.getOutcome());
        latest.setErrorSummary(trace.getErrorSummary());
        latest.setDurationMs(trace.getDurationMs());
        latest.setInputTokens(trace.getInputTokens());
        latest.setOutputTokens(trace.getOutputTokens());
        latest.setOccurredAt(trace.getOccurredAt() == null ? null : trace.getOccurredAt().toString());
        return latest;
    }

    private RagQuickStatusVO toRag(RagStatus status) {
        RagQuickStatusVO rag = new RagQuickStatusVO();
        if (status == null) {
            return rag;
        }
        rag.setEnabled(status.isEnabled());
        rag.setIndexed(status.isIndexed());
        rag.setDocumentCount(status.getDocumentCount());
        rag.setChunkCount(status.getChunkCount());
        rag.setLastError(status.getLastError());
        rag.setLastIndexedAt(status.getLastIndexedAt() != null ? status.getLastIndexedAt().toString() : null);
        return rag;
    }
}
