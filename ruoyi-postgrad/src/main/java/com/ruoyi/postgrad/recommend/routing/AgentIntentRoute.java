package com.ruoyi.postgrad.recommend.routing;

/** High-level capability route selected before the main chat invocation. */
public enum AgentIntentRoute {
    KNOWLEDGE_RAG,
    PROGRAM_FACTS,
    HYBRID,
    DRAFT_READ,
    DRAFT_WRITE,
    CLARIFY,
    FALLBACK
}
