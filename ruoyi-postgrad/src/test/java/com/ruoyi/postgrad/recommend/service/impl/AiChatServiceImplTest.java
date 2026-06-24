package com.ruoyi.postgrad.recommend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.ruoyi.postgrad.mapper.AiChatMapper;
import com.ruoyi.postgrad.recommend.domain.AiChatConversation;
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

        assertTrue(context.contains("湘潭大学 计算机科学与技术 (ID 2357)"));
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

    // ═══════════ H3: chat() 并发锁拒绝 ═══════════

    @Test
    @SuppressWarnings("unchecked")
    void concurrentChatShouldBeRejected() throws Exception {
        // Given: Redis 锁 SETNX 返回 false（已有对话在进行中）
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class)))
            .thenReturn(false); // 锁已被占用

        AiChatMapper aiChatMapper = mock(AiChatMapper.class);
        AiChatConversation conv = new AiChatConversation();
        conv.setId(1L);
        conv.setUserId(1L);
        when(aiChatMapper.selectActiveConversation(1L)).thenReturn(conv);

        AiChatServiceImpl service = new AiChatServiceImpl();
        injectField(service, "redisTemplate", redis);
        injectField(service, "aiChatMapper", aiChatMapper);

        AtomicReference<String> errorMsg = new AtomicReference<>();
        ChatStreamCallback callback = new ChatStreamCallback() {
            @Override public void onToken(String token) {}
            @Override public void onError(Throwable error) {
                errorMsg.set(error.getMessage());
            }
        };

        // When: 尝试发送第二条消息
        service.chat(1L, "并发消息", callback);

        // Then: 被拒绝，错误消息包含"处理中"
        assertNotNull(errorMsg.get());
        assertTrue(errorMsg.get().contains("处理中"),
            "H3: concurrent chat should be rejected: " + errorMsg.get());
    }

    private static void injectField(Object target, String fieldName, Object value) throws Exception {
        Field field = AiChatServiceImpl.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
