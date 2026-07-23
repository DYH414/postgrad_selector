package com.ruoyi.postgrad.rag.eval.answer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RagAnswerEvaluationCase {

    private String id;
    private String query;
    private Map<String, String> expectedMetadata = new LinkedHashMap<>();
    private List<String> requiredClaims = new ArrayList<>();
    private List<String> forbiddenClaims = new ArrayList<>();
    private boolean expectedAbstention;
    private String category;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public Map<String, String> getExpectedMetadata() { return expectedMetadata; }
    public void setExpectedMetadata(Map<String, String> expectedMetadata) {
        this.expectedMetadata = expectedMetadata == null
            ? new LinkedHashMap<>() : new LinkedHashMap<>(expectedMetadata);
    }
    public List<String> getRequiredClaims() { return requiredClaims; }
    public void setRequiredClaims(List<String> requiredClaims) {
        this.requiredClaims = requiredClaims == null ? new ArrayList<>() : requiredClaims;
    }
    public List<String> getForbiddenClaims() { return forbiddenClaims; }
    public void setForbiddenClaims(List<String> forbiddenClaims) {
        this.forbiddenClaims = forbiddenClaims == null ? new ArrayList<>() : forbiddenClaims;
    }
    public boolean isExpectedAbstention() { return expectedAbstention; }
    public void setExpectedAbstention(boolean expectedAbstention) { this.expectedAbstention = expectedAbstention; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}
