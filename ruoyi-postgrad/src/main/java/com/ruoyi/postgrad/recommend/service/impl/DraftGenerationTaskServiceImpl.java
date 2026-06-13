package com.ruoyi.postgrad.recommend.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.postgrad.recommend.domain.DraftGenerationTaskState;
import com.ruoyi.postgrad.recommend.domain.DraftGenerationTaskVO;
import com.ruoyi.postgrad.recommend.service.DraftGenerationCallback;
import com.ruoyi.postgrad.recommend.service.IDraftGenerationTaskService;
import com.ruoyi.postgrad.recommend.service.IDraftService;

@Service
public class DraftGenerationTaskServiceImpl implements IDraftGenerationTaskService {
    static final String TASK_KEY_PREFIX = "ai:v2:draft:task:";
    private static final Duration TASK_TTL = Duration.ofMinutes(60);

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private IDraftService draftService;

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

        CompletableFuture.runAsync(() -> runTask(userId, taskId));

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
