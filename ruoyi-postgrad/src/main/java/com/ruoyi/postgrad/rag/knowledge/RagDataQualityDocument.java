package com.ruoyi.postgrad.rag.knowledge;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class RagDataQualityDocument {

    private final RagProgramHistoryDocument history;

    public RagDataQualityDocument(RagProgramHistoryDocument history) {
        if (history == null) {
            throw new IllegalArgumentException("history must not be null");
        }
        this.history = history;
    }

    public RagProgramHistoryDocument getHistory() {
        return history;
    }

    public RagProgramYearFact getPrimaryFact() {
        return history.getPrimaryFact();
    }

    public List<Integer> getMissingYears() {
        Integer start = history.getYearStart();
        Integer end = history.getYearEnd();
        if (start == null || end == null || start >= end) {
            return List.of();
        }
        Set<Integer> present = new LinkedHashSet<>();
        for (RagProgramYearFact fact : history.getYearFacts()) {
            if (fact.getYear() != null) {
                present.add(fact.getYear());
            }
        }
        List<Integer> missing = new ArrayList<>();
        for (int year = start; year <= end; year++) {
            if (!present.contains(year)) {
                missing.add(year);
            }
        }
        return missing;
    }

    public String getMergedMissingFields() {
        Set<String> fields = new LinkedHashSet<>();
        for (RagProgramYearFact fact : history.getYearFacts()) {
            String missing = fact.getMissingFields();
            if (missing == null || missing.isBlank() || "[]".equals(missing.trim())) {
                continue;
            }
            String cleaned = missing.replace("[", "").replace("]", "").replace("\"", "");
            for (String item : cleaned.split(",")) {
                String field = item.trim();
                if (!field.isEmpty()) {
                    fields.add(field);
                }
            }
        }
        return String.join(",", fields);
    }

    public String getDataCompleteness() {
        if (!getMissingYears().isEmpty() || !getMergedMissingFields().isBlank()) {
            return "partial";
        }
        return "complete";
    }
}
