package com.ruoyi.postgrad.recommend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ruoyi.postgrad.recommend.domain.CandidateCardVO;
import com.ruoyi.postgrad.recommend.domain.DraftVO;
import com.ruoyi.postgrad.recommend.domain.SchoolFact;
import com.ruoyi.postgrad.recommend.domain.TierCandidates;
import com.ruoyi.postgrad.recommend.service.ChatStreamCallback;

class AiChatServiceImplTest {

    @Test
    void chatCallbackForwardsOnlyToolExecutionMetadata() {
        class RecordingCallback implements ChatStreamCallback {
            String message;
            boolean draftChanged;
            String toolResultJson;

            @Override
            public void onToken(String token) {
            }

            @Override
            public void onDone(String fullMessage, boolean draftChanged, String toolActionResultJson) {
                this.message = fullMessage;
                this.draftChanged = draftChanged;
                this.toolResultJson = toolActionResultJson;
            }

            @Override
            public void onError(Throwable error) {
            }
        }

        RecordingCallback callback = new RecordingCallback();

        callback.onDone("ok", true, "{\"ok\":true}");

        assertEquals("ok", callback.message);
        assertTrue(callback.draftChanged);
        assertEquals("{\"ok\":true}", callback.toolResultJson);
    }

    @Test
    void legacyCallbackDoesNotInferDraftActionFromAssistantText() {
        class RecordingCallback implements ChatStreamCallback {
            String message;
            boolean calledWithToolMetadata;

            @Override
            public void onToken(String token) {
            }

            @Override
            public void onDone(String fullMessage) {
                this.message = fullMessage;
            }

            @Override
            public void onDone(String fullMessage, boolean draftChanged, String toolActionResultJson) {
                calledWithToolMetadata = true;
                ChatStreamCallback.super.onDone(fullMessage, draftChanged, toolActionResultJson);
            }

            @Override
            public void onError(Throwable error) {
            }
        }

        RecordingCallback callback = new RecordingCallback();

        callback.onDone("现在移除宁夏大学：", false, null);

        assertEquals("现在移除宁夏大学：", callback.message);
        assertTrue(callback.calledWithToolMetadata);
    }

    @Test
    void draftContextIncludesHiddenOperationIdForTools() {
        SchoolFact fact = new SchoolFact();
        fact.setProgramId(2357L);
        fact.setSchoolName("湘潭大学");
        fact.setProgramName("计算机科学与技术");
        fact.setAvgAdmittedScore(290);
        fact.setScoreGap(10);

        CandidateCardVO card = CandidateCardVO.fromFact(fact);

        TierCandidates tier = new TierCandidates();
        tier.setLabel("稳妥档");
        tier.setTargetCount(4);
        tier.setCandidates(List.of(card));

        DraftVO draft = new DraftVO();
        draft.setTiers(List.of(tier));

        String context = AiChatServiceImpl.buildDraftContextText(draft);

        assertTrue(context.contains("湘潭大学 计算机科学与技术 【操作ID:2357】"));
        assertTrue(context.contains("稳妥档（1/4）"));
    }

    @Test
    void promptContractDoesNotDependOnTextActionFallback() throws Exception {
        String prompt = Files.readString(
            Path.of("src/main/resources/prompts/v2/chat-system.txt"),
            StandardCharsets.UTF_8);

        assertFalse(prompt.contains("---ACTION---"));
        assertFalse(prompt.contains("\"type\":\"remove\""));
        assertTrue(prompt.contains("removeDraftCandidate(programId)"));
    }
}
