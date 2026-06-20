package com.ruoyi.postgrad.recommend.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.postgrad.mapper.AiChatMapper;
import com.ruoyi.postgrad.recommend.domain.AiChatConversation;
import com.ruoyi.postgrad.recommend.domain.AiChatMessage;
import com.ruoyi.postgrad.recommend.domain.DraftGenerationTaskState;
import com.ruoyi.postgrad.recommend.domain.DraftGenerationTaskVO;
import com.ruoyi.postgrad.recommend.domain.RecommendationProgressEvent;
import com.ruoyi.postgrad.recommend.service.DraftGenerationCallback;
import com.ruoyi.postgrad.recommend.service.IDraftGenerationTaskService;
import com.ruoyi.postgrad.recommend.service.IDraftService;
import com.ruoyi.postgrad.recommend.service.IDraftSummaryMessageService;

@Service
public class DraftGenerationTaskServiceImpl implements IDraftGenerationTaskService {
    static final String TASK_KEY_PREFIX = "ai:v2:draft:task:";
    private static final Duration TASK_TTL = Duration.ofMinutes(60);

    private static final Logger log = LoggerFactory.getLogger(DraftGenerationTaskServiceImpl.class);

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private IDraftService draftService;

    @Autowired
    private IDraftSummaryMessageService summaryMessageService;

    @Autowired
    private AiChatMapper aiChatMapper;

    @Autowired
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

    @Override
    public DraftGenerationTaskVO start(Long userId) {
        String taskId = UUID.randomUUID().toString();
        String streamToken = UUID.randomUUID().toString().replace("-", "");

        DraftGenerationTaskState state = new DraftGenerationTaskState();
        state.setTaskId(taskId);
        state.setUserId(userId);
        state.setStatus(DraftGenerationTaskState.STATUS_RUNNING);
        state.setPhase("queued");
        state.setMessage("正在准备生成草稿...");
        state.setStreamTokenHash(hash(streamToken));
        state.setUpdatedAt(System.currentTimeMillis());
        save(state);

        CompletableFuture.runAsync(() -> runTask(userId, taskId), threadPoolTaskExecutor);

        DraftGenerationTaskVO vo = new DraftGenerationTaskVO();
        vo.setTaskId(taskId);
        vo.setStreamToken(streamToken);
        vo.setStatus(DraftGenerationTaskState.STATUS_RUNNING);
        return vo;
    }

    @Override
    public DraftGenerationTaskState getState(String taskId) {
        String json = redisTemplate.opsForValue().get(key(taskId));
        if (json == null || json.isBlank()) {
            return null;
        }
        return JSON.parseObject(json, DraftGenerationTaskState.class);
    }

    @Override
    public boolean validateStreamToken(String taskId, String streamToken) {
        DraftGenerationTaskState state = getState(taskId);
        return state != null && state.getStreamTokenHash() != null
            && state.getStreamTokenHash().equals(hash(streamToken));
    }

    private void runTask(Long userId, String taskId) {
        draftService.generateDraft(userId, new DraftGenerationCallback() {
            @Override
            public void onTierComplete(String tier, String tierJson) {
                DraftGenerationTaskState state = getState(taskId);
                if (state == null) return;
                state.setTier(tier);
                state.setTierJson(tierJson);
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("phase", "ai_selecting");
                payload.put("message", "档位完成");
                payload.put("tier", tier);
                try {
                    payload.put("tierData", JSON.parse(tierJson));
                } catch (Exception ignored) {
                    payload.put("tierData", tierJson);
                }
                appendStreamEvent(state, payload);
                state.setUpdatedAt(System.currentTimeMillis());
                save(state);
            }

            @Override
            public void onProgress(RecommendationProgressEvent event) {
                DraftGenerationTaskState state = getState(taskId);
                if (state == null || event == null) {
                    return;
                }
                state.setStatus(DraftGenerationTaskState.STATUS_RUNNING);
                state.setPhase(event.getPhase());
                state.setMessage(event.getMessage());
                state.setFound(event.getAfterCount());
                state.setTier(event.getTier());
                state.setProgressJson(JSON.toJSONString(event));
                appendStreamEvent(state, event);
                state.setUpdatedAt(System.currentTimeMillis());
                save(state);
            }

            @Override
            public void onProgress(String phase, String message, Integer found, String tier) {
                DraftGenerationTaskState state = getState(taskId);
                if (state == null) {
                    return;
                }
                state.setStatus(DraftGenerationTaskState.STATUS_RUNNING);
                state.setPhase(phase);
                state.setMessage(message);
                state.setFound(found);
                state.setTier(tier);
                state.setProgressJson(null);
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("phase", phase);
                payload.put("message", message);
                if (found != null) payload.put("found", found);
                if (tier != null) payload.put("tier", tier);
                appendStreamEvent(state, payload);
                state.setUpdatedAt(System.currentTimeMillis());
                save(state);
            }

            @Override
            public void onDone(com.ruoyi.postgrad.recommend.domain.DraftVO draft,
                               com.ruoyi.postgrad.recommend.domain.ProfileBasisVO profileBasis,
                               int removedCount) {
                DraftGenerationTaskState state = getState(taskId);
                if (state == null) {
                    return;
                }
                state.setStatus(DraftGenerationTaskState.STATUS_DONE);
                state.setPhase("done");
                state.setMessage("草稿生成完成");
                state.setDraftJson(JSON.toJSONString(draft));
                state.setProfileBasisJson(JSON.toJSONString(profileBasis));
                state.setRemovedCount(removedCount);
                // Generate and persist completion summary
                String summary = summaryMessageService.generateSummary(draft);
                if (summary != null) {
                    state.setSummaryMessage(summary);
                    saveSummaryAsChatMessage(userId, summary);
                }
                state.setUpdatedAt(System.currentTimeMillis());
                save(state);
            }

            @Override
            public void onError(Throwable error) {
                DraftGenerationTaskState state = getState(taskId);
                if (state == null) {
                    return;
                }
                state.setStatus(DraftGenerationTaskState.STATUS_ERROR);
                state.setPhase("error");
                state.setMessage("生成失败");
                state.setErrorMessage(error.getMessage());
                state.setUpdatedAt(System.currentTimeMillis());
                save(state);
            }
        });
    }

    private void save(DraftGenerationTaskState state) {
        redisTemplate.opsForValue().set(key(state.getTaskId()), JSON.toJSONString(state), TASK_TTL);
    }

    private void appendStreamEvent(DraftGenerationTaskState state, Object payload) {
        List<Object> events = new ArrayList<>();
        if (state.getStreamEventsJson() != null && !state.getStreamEventsJson().isBlank()) {
            try {
                events.addAll(JSON.parseArray(state.getStreamEventsJson(), Object.class));
            } catch (Exception ignored) {
                events.clear();
            }
        }
        events.add(payload);
        state.setStreamEventsJson(JSON.toJSONString(events));
    }

    private void saveSummaryAsChatMessage(Long userId, String summary) {
        try {
            AiChatConversation conv = aiChatMapper.selectActiveConversation(userId);
            if (conv == null) {
                conv = new AiChatConversation();
                conv.setUserId(userId);
                conv.setStatus("active");
                aiChatMapper.insertConversation(conv);
            }
            int nextSeq = aiChatMapper.selectNextSeq(conv.getId());
            AiChatMessage msg = new AiChatMessage();
            msg.setConversationId(conv.getId());
            msg.setUserId(userId);
            msg.setRole("assistant");
            msg.setContent(summary);
            msg.setDisplayContent(summary);
            msg.setMessageType("draft_summary");
            msg.setStatus("completed");
            msg.setSeq(nextSeq);
            aiChatMapper.insertMessage(msg);
            aiChatMapper.touchConversation(conv.getId());
        } catch (Exception e) {
            log.error("[Summary] Failed to save summary as chat message: {}", e.getMessage());
        }
    }

    private static String key(String taskId) {
        return TASK_KEY_PREFIX + taskId;
    }

    private static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash stream token", e);
        }
    }

    void setRedisTemplateForTest(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    void setDraftServiceForTest(IDraftService draftService) {
        this.draftService = draftService;
    }
}
