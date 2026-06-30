package com.ruoyi.postgrad.observe.service.impl;

import com.ruoyi.postgrad.observe.domain.AiObserveGroupVO;
import com.ruoyi.postgrad.observe.domain.AiObserveLatestVO;
import com.ruoyi.postgrad.observe.domain.AiObserveOverviewVO;
import com.ruoyi.postgrad.observe.domain.RagQuickStatusVO;
import com.ruoyi.postgrad.observe.runtime.AiInvocationSnapshot;
import com.ruoyi.postgrad.observe.runtime.AiInvocationSnapshotRecorder;
import com.ruoyi.postgrad.observe.service.AiObserveService;
import com.ruoyi.postgrad.rag.RagDocumentService;
import com.ruoyi.postgrad.rag.RagStatus;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AiObserveServiceImpl implements AiObserveService {

    private static final String TOKEN_USAGE = "gen_ai.client.token.usage";
    private static final String OPERATION_DURATION = "gen_ai.client.operation.duration";

    private final MeterRegistry meterRegistry;
    private final RagDocumentService ragDocumentService;
    private final AiInvocationSnapshotRecorder snapshotRecorder;

    public AiObserveServiceImpl(MeterRegistry meterRegistry,
                                RagDocumentService ragDocumentService,
                                AiInvocationSnapshotRecorder snapshotRecorder) {
        this.meterRegistry = meterRegistry;
        this.ragDocumentService = ragDocumentService;
        this.snapshotRecorder = snapshotRecorder;
    }

    @Override
    public AiObserveOverviewVO getOverview() {
        AiObserveOverviewVO overview = new AiObserveOverviewVO();
        overview.setInputTokens(totalTokenUsage("input"));
        overview.setOutputTokens(totalTokenUsage("output"));
        overview.setAverageDurationMs(averageDurationMs());
        overview.setTotalCalls(totalCalls());
        overview.setSuccessRate(successRate(overview.getTotalCalls()));
        overview.setModelGroups(groupByTag("gen_ai.request.model"));
        overview.setOperationGroups(groupByTag("postgrad.ai.operation"));
        overview.setLatest(toLatest(snapshotRecorder.getLatest()));
        overview.setRag(toRag(ragDocumentService.getStatus()));
        return overview;
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
