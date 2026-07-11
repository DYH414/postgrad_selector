package com.ruoyi.postgrad.rag.service;

import com.ruoyi.postgrad.rag.dto.RagChunkResult;
import com.ruoyi.postgrad.rag.dto.RagEvidenceRequest;
import com.ruoyi.postgrad.rag.dto.RagEvidenceResponse;
import com.ruoyi.postgrad.rag.dto.RagSearchTestRequest;
import com.ruoyi.postgrad.rag.dto.RagSearchTestResponse;
import com.ruoyi.postgrad.rag.knowledge.RagCitation;
import com.ruoyi.postgrad.rag.trace.RagTrace;
import com.ruoyi.postgrad.rag.trace.RagTraceRecorder;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Program-scoped RAG evidence retrieval for recommendation explanations.
 */
@Service
public class RagEvidenceService {

    private final RagRetrievalService ragRetrievalService;
    private final RagTraceRecorder traceRecorder;

    @Autowired
    public RagEvidenceService(RagRetrievalService ragRetrievalService, RagTraceRecorder traceRecorder) {
        this.ragRetrievalService = ragRetrievalService;
        this.traceRecorder = traceRecorder;
    }

    public RagEvidenceService(RagRetrievalService ragRetrievalService) {
        this(ragRetrievalService, null);
    }

    public RagEvidenceResponse retrieveProgramEvidence(RagEvidenceRequest request) {
        RagSearchTestResponse strict = retrieve(toSearchRequest(request, false));
        if (hasEvidence(strict)) {
            RagEvidenceResponse response = toEvidenceResponse(request, strict, false, null);
            recordTrace(request, strict, response);
            return response;
        }

        if (shouldFallback(request)) {
            RagSearchTestResponse fallback = retrieve(toSearchRequest(request, true));
            RagEvidenceResponse response = toEvidenceResponse(request, fallback, true, "program_broad");
            recordTrace(request, fallback, response);
            return response;
        }

        RagEvidenceResponse response = toEvidenceResponse(request, strict, false, null);
        recordTrace(request, strict, response);
        return response;
    }

    private RagSearchTestRequest toSearchRequest(RagEvidenceRequest request, boolean broad) {
        RagSearchTestRequest search = new RagSearchTestRequest();
        search.setQuery(request != null ? request.getQuery() : null);
        search.setMaxResults(request != null ? request.getMaxResults() : null);
        search.setMinScore(request != null ? request.getMinScore() : null);
        search.setRerankTopN(request != null ? request.getRerankTopN() : null);
        if (request == null) {
            return search;
        }
        search.setProgramId(value(request.getProgramId()));
        search.setSchoolId(value(request.getSchoolId()));
        if (!broad) {
            search.setYear(value(request.getYear()));
            search.setDocType(request.getDocType());
            search.setSection(request.getSection());
        }
        return search;
    }

    private RagSearchTestResponse retrieve(RagSearchTestRequest request) {
        return ragRetrievalService.retrieve(request).response();
    }

    private RagEvidenceResponse toEvidenceResponse(RagEvidenceRequest request, RagSearchTestResponse search,
                                                   boolean fallbackUsed, String fallbackMode) {
        RagEvidenceResponse response = new RagEvidenceResponse();
        response.setQuery(request != null ? request.getQuery() : null);
        response.setFallbackUsed(fallbackUsed);
        response.setFallbackMode(fallbackMode);
        if (search == null) {
            return response;
        }
        response.setErrorSummary(search.getErrorSummary());
        response.setAppliedFilters(search.getAppliedFilters());
        List<RagChunkResult> selected = search.getChunks().stream()
            .filter(RagChunkResult::isSelectedForPrompt)
            .toList();
        if (selected.isEmpty()) {
            selected = search.getChunks();
        }
        response.setSelectedChunks(selected);
        response.setCitations(selected.stream()
            .map(RagCitation::fromChunkResult)
            .toList());
        return response;
    }

    private boolean hasEvidence(RagSearchTestResponse response) {
        return response != null && response.getChunks() != null && !response.getChunks().isEmpty();
    }

    private boolean shouldFallback(RagEvidenceRequest request) {
        return request != null
            && request.getProgramId() != null
            && (notBlank(request.getDocType()) || notBlank(request.getSection()) || request.getYear() != null);
    }

    private void recordTrace(RagEvidenceRequest request, RagSearchTestResponse search, RagEvidenceResponse evidence) {
        if (traceRecorder == null || search == null) {
            return;
        }
        RagTrace trace = new RagTrace();
        trace.setOperationKey("recommendation-evidence");
        trace.setRunId(request != null ? request.getRunId() : null);
        trace.setRetrievalRoute("scoped-evidence");
        trace.setQuery(request != null ? request.getQuery() : null);
        trace.setOutcome(search.getErrorSummary() == null ? RagTrace.Outcome.SUCCESS : RagTrace.Outcome.FAILURE);
        trace.setFailureStage(search.getErrorSummary() != null ? "retrieval" : null);
        trace.setErrorSummary(search.getErrorSummary());
        trace.setRetrievedCount(search.getRetrievedCount());
        trace.setSelectedCount(evidence != null ? evidence.getSelectedChunks().size() : search.getSelectedCount());
        trace.setMetadataFilters(evidence != null ? evidence.getAppliedFilters() : search.getAppliedFilters());
        trace.setFallbackUsed(evidence != null && evidence.isFallbackUsed());
        trace.setFallbackMode(evidence != null ? evidence.getFallbackMode() : null);
        trace.setTopSources((evidence != null ? evidence.getSelectedChunks() : search.getChunks()).stream()
            .map(RagChunkResult::getSourceName)
            .filter(source -> source != null && !source.isBlank())
            .distinct()
            .limit(5)
            .toList());
        trace.setSelectedChunks((evidence != null ? evidence.getSelectedChunks() : search.getChunks()).stream()
            .map(chunk -> new RagTrace.SelectedChunk(chunk.getSourceName(), chunk.getContentPreview()))
            .limit(5)
            .toList());
        if (search.getLatency() != null) {
            trace.setEmbeddingLatencyMs(search.getLatency().getEmbeddingMs());
            trace.setRetrievalLatencyMs(search.getLatency().getRetrievalMs());
            trace.setRerankLatencyMs(search.getLatency().getRerankMs());
            trace.setTotalLatencyMs(search.getLatency().getTotalMs());
        }
        traceRecorder.record(trace);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String value(Object value) {
        return value != null ? String.valueOf(value) : null;
    }
}
