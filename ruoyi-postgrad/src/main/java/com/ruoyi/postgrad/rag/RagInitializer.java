package com.ruoyi.postgrad.rag;

import com.ruoyi.postgrad.rag.qdrant.RagCollectionManager;
import com.ruoyi.postgrad.rag.status.RagIndexStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class RagInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RagInitializer.class);

    private static final String ACTIVE_ALIAS = "postgrad_rag_active";

    private final RagIndexStatusService indexStatusService;
    private final RagCollectionManager collectionManager;

    public RagInitializer(RagIndexStatusService indexStatusService,
                          RagCollectionManager collectionManager) {
        this.indexStatusService = indexStatusService;
        this.collectionManager = collectionManager;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 1. Mark any stale index runs as INTERRUPTED
        indexStatusService.markPendingInterrupted();

        // 2. Check whether a RAG index already exists
        try {
            String activeCollection = collectionManager.resolveAlias(ACTIVE_ALIAS);

            if (activeCollection != null) {
                long pointCount = collectionManager.getPointCount(activeCollection);
                if (pointCount >= 0) {
                    log.info("[RagInit] RAG index found: {} (points: {})", activeCollection, pointCount);
                } else {
                    log.warn("[RagInit] RAG index found: {} but collection is unreachable", activeCollection);
                }
            } else {
                log.info("[RagInit] RAG NOT_INITIALIZED — run POST /postgrad/rag/index-runs to build");
            }
        } catch (Exception ex) {
            log.warn("[RagInit] Failed to check RAG index status: {}", ex.getMessage());
        }
    }
}
