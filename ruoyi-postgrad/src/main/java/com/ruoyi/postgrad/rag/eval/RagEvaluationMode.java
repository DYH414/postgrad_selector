package com.ruoyi.postgrad.rag.eval;

/** Retrieval configuration compared by the frozen evaluation ablation. */
public enum RagEvaluationMode {
    VECTOR_ONLY(true),
    VECTOR_RERANK(false);

    private final boolean skipRerank;

    RagEvaluationMode(boolean skipRerank) {
        this.skipRerank = skipRerank;
    }

    public boolean isSkipRerank() {
        return skipRerank;
    }
}
