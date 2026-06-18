package com.ruoyi.postgrad.recommend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.ruoyi.postgrad.recommend.domain.DraftVO;
import com.ruoyi.postgrad.recommend.domain.ProfileBasisVO;
import com.ruoyi.postgrad.recommend.domain.RecommendationProgressEvent;

class DraftGenerationCallbackTest {

    @Test
    void structuredProgressFallsBackToLegacyProgressMethod() {
        LegacyCallback callback = new LegacyCallback();
        RecommendationProgressEvent event = RecommendationProgressEvent.success(
            "select_safe", "AI选择保底档", 10, 3, "safe");

        callback.onProgress(event);

        assertEquals("select_safe", callback.phase);
        assertEquals("AI选择保底档（10→3）", callback.message);
        assertEquals(3, callback.found);
        assertEquals("safe", callback.tier);
    }

    private static class LegacyCallback implements DraftGenerationCallback {
        private String phase;
        private String message;
        private Integer found;
        private String tier;

        @Override
        public void onProgress(String phase, String message, Integer found, String tier) {
            this.phase = phase;
            this.message = message;
            this.found = found;
            this.tier = tier;
        }

        @Override
        public void onDone(DraftVO draft, ProfileBasisVO profileBasis, int removedCount) {
        }

        @Override
        public void onError(Throwable error) {
        }
    }
}
