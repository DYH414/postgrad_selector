package com.ruoyi.postgrad.recommend.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class RecommendationProgressEventTest {

    @Test
    void successWithCountsBuildsCountMessage() {
        RecommendationProgressEvent event = RecommendationProgressEvent.success(
            "candidate_pool", "构建候选池", 82, 30, null);

        assertEquals("recommendation_progress", event.getType());
        assertEquals("candidate_pool", event.getPhase());
        assertEquals("success", event.getStatus());
        assertEquals("构建候选池", event.getTitle());
        assertEquals("构建候选池（82→30）", event.getMessage());
        assertEquals(82, event.getBeforeCount());
        assertEquals(30, event.getAfterCount());
        assertNull(event.getTier());
    }

    @Test
    void successWithoutBeforeCountFallsBackToAfterCountOnly() {
        RecommendationProgressEvent event = RecommendationProgressEvent.success(
            "finalize", "生成候选草稿", null, 10, null);

        assertEquals("生成候选草稿（10 所）", event.getMessage());
        assertNull(event.getBeforeCount());
        assertEquals(10, event.getAfterCount());
    }

    @Test
    void runningUsesProvidedMessage() {
        RecommendationProgressEvent event = RecommendationProgressEvent.running(
            "select_reach", "AI选择冲刺档", "AI正在选择冲刺档...", 12, "reach");

        assertEquals("running", event.getStatus());
        assertEquals("AI正在选择冲刺档...", event.getMessage());
        assertEquals(12, event.getBeforeCount());
        assertNull(event.getAfterCount());
        assertEquals("reach", event.getTier());
    }

    @Test
    void errorIncludesDetail() {
        RecommendationProgressEvent event = RecommendationProgressEvent.error(
            "filter_408", "筛选408专业", "筛选408专业失败", "数据库连接失败", null);

        assertEquals("error", event.getStatus());
        assertEquals("筛选408专业失败", event.getMessage());
        assertEquals("数据库连接失败", event.getDetail());
    }
}
