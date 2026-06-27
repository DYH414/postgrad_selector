package com.ruoyi.postgrad.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class RagInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RagInitializer.class);

    private final RagDocumentService ragDocumentService;

    @Value("${app.ai.rag.indexing.auto-index-on-startup:true}")
    private boolean autoIndexOnStartup;

    @Value("${app.ai.rag.embedding.api-key:}")
    private String apiKey;

    public RagInitializer(RagDocumentService ragDocumentService) {
        this.ragDocumentService = ragDocumentService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!autoIndexOnStartup) {
            return;
        }
        try {
            ragDocumentService.reindex();
        } catch (Exception ex) {
            log.error("[RagInit] Indexing failed, RAG will be disabled for this run: {}", ex.getMessage(), ex);
        }
    }
}
