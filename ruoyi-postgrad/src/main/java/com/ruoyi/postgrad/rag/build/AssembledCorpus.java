package com.ruoyi.postgrad.rag.build;

import dev.langchain4j.data.segment.TextSegment;
import java.util.List;

/**
 * Result of assembling a unified RAG corpus from general documents
 * and database evidence.
 *
 * @param chunks               All assembled chunks as TextSegments with unified metadata.
 * @param generalDocumentCount Number of general source documents (markdown files from classpath).
 * @param databaseDocumentCount Number of canonical database documents (histories + facts + quality docs).
 */
public record AssembledCorpus(
    List<TextSegment> chunks,
    int generalDocumentCount,
    int databaseDocumentCount
) {}
