package com.ruoyi.postgrad.domain.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AiToolTrace {
    private final List<Map<String, Object>> calls = new ArrayList<>();
    private final Set<Long> detailedProgramIds = new LinkedHashSet<>();
    private final Set<Long> expandedProgramIds = new LinkedHashSet<>();
    /** searchPrograms 返回的 programId（用于 autoFillBookmarks 兜底） */
    private final Set<Long> searchedProgramIds = new LinkedHashSet<>();
    private int removedIncompleteCount;
    private boolean explorationLimited;

    public void record(String toolName, Map<String, Object> args, Object resultSummary) {
        Map<String, Object> call = new LinkedHashMap<>();
        call.put("toolName", toolName);
        call.put("args", args);
        call.put("resultSummary", resultSummary);
        calls.add(call);
    }

    public void recordDetail(long programId) {
        detailedProgramIds.add(programId);
    }

    public void recordSearchResult(long programId) {
        searchedProgramIds.add(programId);
    }

    public void recordExpanded(long programId) {
        expandedProgramIds.add(programId);
    }

    public boolean hasDetail(long programId) {
        return detailedProgramIds.contains(programId);
    }

    public Set<Long> getSearchedProgramIds() {
        return searchedProgramIds;
    }

    public List<Map<String, Object>> getCalls() {
        return calls;
    }

    public int getRemovedIncompleteCount() {
        return removedIncompleteCount;
    }

    public void setRemovedIncompleteCount(int removedIncompleteCount) {
        this.removedIncompleteCount = Math.max(0, removedIncompleteCount);
    }

    public boolean isExplorationLimited() {
        return explorationLimited;
    }

    public void setExplorationLimited(boolean explorationLimited) {
        this.explorationLimited = explorationLimited;
    }
}
