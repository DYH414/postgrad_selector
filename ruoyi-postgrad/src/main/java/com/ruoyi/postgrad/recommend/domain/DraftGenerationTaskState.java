package com.ruoyi.postgrad.recommend.domain;

public class DraftGenerationTaskState {
    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_DONE = "done";
    public static final String STATUS_ERROR = "error";

    private String taskId;
    private Long userId;
    private String status;
    private String phase;
    private String message;
    private Integer found;
    private String tier;
    private String progressJson;
    private String tierJson;
    private String streamEventsJson;
    private String draftJson;
    private String profileBasisJson;
    private Integer removedCount;
    private String errorMessage;
    private String streamTokenHash;
    private Long updatedAt;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Integer getFound() {
        return found;
    }

    public void setFound(Integer found) {
        this.found = found;
    }

    public String getTier() {
        return tier;
    }

    public void setTier(String tier) {
        this.tier = tier;
    }

    public String getProgressJson() {
        return progressJson;
    }

    public void setProgressJson(String progressJson) {
        this.progressJson = progressJson;
    }

    public String getTierJson() {
        return tierJson;
    }

    public void setTierJson(String tierJson) {
        this.tierJson = tierJson;
    }

    public String getStreamEventsJson() {
        return streamEventsJson;
    }

    public void setStreamEventsJson(String streamEventsJson) {
        this.streamEventsJson = streamEventsJson;
    }

    public String getDraftJson() {
        return draftJson;
    }

    public void setDraftJson(String draftJson) {
        this.draftJson = draftJson;
    }

    public String getProfileBasisJson() {
        return profileBasisJson;
    }

    public void setProfileBasisJson(String profileBasisJson) {
        this.profileBasisJson = profileBasisJson;
    }

    public Integer getRemovedCount() {
        return removedCount;
    }

    public void setRemovedCount(Integer removedCount) {
        this.removedCount = removedCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getStreamTokenHash() {
        return streamTokenHash;
    }

    public void setStreamTokenHash(String streamTokenHash) {
        this.streamTokenHash = streamTokenHash;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
