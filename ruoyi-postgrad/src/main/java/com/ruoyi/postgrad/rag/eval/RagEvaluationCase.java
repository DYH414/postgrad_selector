package com.ruoyi.postgrad.rag.eval;

import java.util.LinkedHashMap;
import java.util.Map;

public class RagEvaluationCase {

    private String id;
    private String query;
    private Map<String, String> expectedMetadata = new LinkedHashMap<>();
    private boolean sourceDataAvailable = true;

    public RagEvaluationCase() {
    }

    public RagEvaluationCase(String id, String query, Map<String, String> expectedMetadata) {
        this.id = id;
        this.query = query;
        this.expectedMetadata = new LinkedHashMap<>(expectedMetadata);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public Map<String, String> getExpectedMetadata() { return expectedMetadata; }
    public void setExpectedMetadata(Map<String, String> expectedMetadata) {
        this.expectedMetadata = expectedMetadata != null ? new LinkedHashMap<>(expectedMetadata) : new LinkedHashMap<>();
    }
    public boolean isSourceDataAvailable() { return sourceDataAvailable; }
    public void setSourceDataAvailable(boolean sourceDataAvailable) { this.sourceDataAvailable = sourceDataAvailable; }
}
