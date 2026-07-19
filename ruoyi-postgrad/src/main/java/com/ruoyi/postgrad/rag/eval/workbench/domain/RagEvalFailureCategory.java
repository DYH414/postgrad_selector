package com.ruoyi.postgrad.rag.eval.workbench.domain;

public enum RagEvalFailureCategory {
    NO_RECALL, WRONG_SCOPE, WRONG_RANK, FILTER_MISMATCH, RERANK_REGRESSION,
    FALSE_POSITIVE, SOURCE_DATA_MISSING, TECHNICAL_ERROR, OTHER
}
