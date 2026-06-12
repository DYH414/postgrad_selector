package com.ruoyi.postgrad.recommend.domain;

public class DraftGenerationTaskVO {
    private String taskId;
    private String streamToken;
    private String status;
    private String message;

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getStreamToken() { return streamToken; }
    public void setStreamToken(String streamToken) { this.streamToken = streamToken; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
