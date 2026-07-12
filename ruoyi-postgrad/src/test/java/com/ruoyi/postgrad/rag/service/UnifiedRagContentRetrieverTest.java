package com.ruoyi.postgrad.rag.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ruoyi.postgrad.rag.dto.RagSearchTestResponse;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UnifiedRagContentRetrieverTest {

    @Test
    void retrieveShouldPassFullSelectedSegmentsToGenericChat() {
        RagRetrievalService retrievalService = mock(RagRetrievalService.class);
        String fullContent = "深圳大学近三年复试线与录取趋势。".repeat(30);
        TextSegment selected = TextSegment.from(fullContent, new Metadata().put("source_id", "sz-2025"));
        when(retrievalService.retrieve(any())).thenReturn(new RagRetrievalResult(
            new RagSearchTestResponse(), List.of(selected)));
        UnifiedRagContentRetriever retriever = new UnifiedRagContentRetriever(
            retrievalService, 5, 0.6, 3);

        List<Content> contents = retriever.retrieve(Query.from("深圳大学录取趋势"));

        assertEquals(List.of(fullContent), contents.stream().map(content -> content.textSegment().text()).toList());
        ArgumentCaptor<com.ruoyi.postgrad.rag.dto.RagSearchTestRequest> requestCaptor =
            ArgumentCaptor.forClass(com.ruoyi.postgrad.rag.dto.RagSearchTestRequest.class);
        verify(retrievalService).retrieve(requestCaptor.capture());
        assertEquals("深圳大学录取趋势", requestCaptor.getValue().getQuery());
        assertEquals(5, requestCaptor.getValue().getMaxResults());
        assertEquals(3, requestCaptor.getValue().getRerankTopN());
    }
}
