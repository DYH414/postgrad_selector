    package com.ruoyi.postgrad.recommend.domain;

/**
 * 草稿生成推荐进度事件。
 */
public class RecommendationProgressEvent {

    private String type = "recommendation_progress";
    private String phase;
    private String status;
    private String title;
    private String message;
    private Integer beforeCount;
    private Integer afterCount;
    private String tier;
    private String detail;
    private Long timestamp;

    public static RecommendationProgressEvent running(String phase, String title, String message,
                                                      Integer beforeCount, String tier) {
        RecommendationProgressEvent event = new RecommendationProgressEvent();
        event.setPhase(phase);
        event.setStatus("running");
        event.setTitle(title);
        event.setMessage(message);
        event.setBeforeCount(beforeCount);
        event.setTier(tier);
        event.setTimestamp(System.currentTimeMillis());
        return event;
    }

    public static RecommendationProgressEvent success(String phase, String title, Integer beforeCount,
                                                      Integer afterCount, String tier) {
        RecommendationProgressEvent event = new RecommendationProgressEvent();
        event.setPhase(phase);
        event.setStatus("success");
        event.setTitle(title);
        event.setBeforeCount(beforeCount);
        event.setAfterCount(afterCount);
        event.setTier(tier);
        event.setMessage(buildSuccessMessage(title, beforeCount, afterCount));
        event.setTimestamp(System.currentTimeMillis());
        return event;
    }

    public static RecommendationProgressEvent error(String phase, String title, String message,
                                                    String detail, String tier) {
        RecommendationProgressEvent event = new RecommendationProgressEvent();
        event.setPhase(phase);
        event.setStatus("error");
        event.setTitle(title);
        event.setMessage(message);
        event.setDetail(detail);
        event.setTier(tier);
        event.setTimestamp(System.currentTimeMillis());
        return event;
    }

    private static String buildSuccessMessage(String title, Integer beforeCount, Integer afterCount) {
        if (beforeCount != null && afterCount != null) {
            return title + "（" + beforeCount + "→" + afterCount + "）";
        }
        if (afterCount != null) {
            return title + "（" + afterCount + " 所）";
        }
        return title;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Integer getBeforeCount() {
        return beforeCount;
    }

    public void setBeforeCount(Integer beforeCount) {
        this.beforeCount = beforeCount;
    }

    public Integer getAfterCount() {
        return afterCount;
    }

    public void setAfterCount(Integer afterCount) {
        this.afterCount = afterCount;
    }

    public String getTier() {
        return tier;
    }

    public void setTier(String tier) {
        this.tier = tier;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
}
