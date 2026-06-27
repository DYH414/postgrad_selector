package com.ruoyi.postgrad.rag;

public class RagIndexResult {

    private final int documentCount;
    private final int chunkCount;

    public RagIndexResult(int documentCount, int chunkCount) {
        this.documentCount = documentCount;
        this.chunkCount = chunkCount;
    }

    public int getDocumentCount() {
        return documentCount;
    }

    public int getChunkCount() {
        return chunkCount;
    }
}
