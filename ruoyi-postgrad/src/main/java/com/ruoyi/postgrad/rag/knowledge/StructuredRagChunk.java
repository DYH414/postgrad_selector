package com.ruoyi.postgrad.rag.knowledge;

import java.util.LinkedHashMap;
import java.util.Map;

public class StructuredRagChunk {

    private final String chunkId;
    private final String chunkTitle;
    private final String text;
    private final Map<String, String> metadata;

    public StructuredRagChunk(String chunkId, String chunkTitle, String text, Map<String, String> metadata) {
        this.chunkId = chunkId;
        this.chunkTitle = chunkTitle;
        this.text = text;
        this.metadata = new LinkedHashMap<>(metadata);
    }

    public String getChunkId() { return chunkId; }
    public String getChunkTitle() { return chunkTitle; }
    public String getText() { return text; }
    public Map<String, String> getMetadata() { return metadata; }
}
