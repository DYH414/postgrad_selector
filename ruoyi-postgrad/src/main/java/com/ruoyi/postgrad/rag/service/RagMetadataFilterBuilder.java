package com.ruoyi.postgrad.rag.service;

import com.ruoyi.postgrad.rag.dto.RagSearchTestRequest;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import java.util.Map;

final class RagMetadataFilterBuilder {

    private RagMetadataFilterBuilder() {
    }

    static Filter fromRequest(RagSearchTestRequest request) {
        if (request == null) {
            return null;
        }
        Filter filter = null;
        for (Map.Entry<String, String> entry : request.getMetadataFilters().entrySet()) {
            Filter next = MetadataFilterBuilder.metadataKey(entry.getKey()).isEqualTo(entry.getValue());
            filter = filter == null ? next : filter.and(next);
        }
        return filter;
    }
}
