package com.ruoyi.postgrad.rag.knowledge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class RagProgramHistoryDocument {

    private final List<RagProgramYearFact> yearFacts;

    public RagProgramHistoryDocument(List<RagProgramYearFact> facts) {
        if (facts == null || facts.isEmpty()) {
            throw new IllegalArgumentException("program history facts must not be empty");
        }
        this.yearFacts = new ArrayList<>(facts);
        this.yearFacts.sort(Comparator.comparing(RagProgramYearFact::getYear,
            Comparator.nullsLast(Integer::compareTo)));
    }

    public List<RagProgramYearFact> getYearFacts() {
        return List.copyOf(yearFacts);
    }

    public RagProgramYearFact getPrimaryFact() {
        return yearFacts.get(yearFacts.size() - 1);
    }

    public Integer getYearStart() {
        return yearFacts.stream()
            .map(RagProgramYearFact::getYear)
            .filter(year -> year != null)
            .min(Integer::compareTo)
            .orElse(null);
    }

    public Integer getYearEnd() {
        return yearFacts.stream()
            .map(RagProgramYearFact::getYear)
            .filter(year -> year != null)
            .max(Integer::compareTo)
            .orElse(null);
    }

    public String getYearsAvailableCsv() {
        return yearFacts.stream()
            .map(RagProgramYearFact::getYear)
            .filter(year -> year != null)
            .map(String::valueOf)
            .distinct()
            .collect(Collectors.joining(","));
    }
}
