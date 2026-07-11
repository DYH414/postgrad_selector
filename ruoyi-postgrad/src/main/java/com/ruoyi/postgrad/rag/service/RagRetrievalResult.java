package com.ruoyi.postgrad.rag.service;

import com.ruoyi.postgrad.rag.dto.RagSearchTestResponse;
import dev.langchain4j.data.segment.TextSegment;
import java.util.List;
import java.util.Objects;

/**
 * Internal retrieval result that keeps full selected content separate from the API-safe preview DTO.
 */
public record RagRetrievalResult(RagSearchTestResponse response, List<TextSegment> selectedSegments) {

    public RagRetrievalResult {
        response = Objects.requireNonNull(response, "response");
        selectedSegments = selectedSegments == null ? List.of() : List.copyOf(selectedSegments);
    }
}
