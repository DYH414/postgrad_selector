package com.ruoyi.postgrad.recommend.service.impl;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.postgrad.recommend.domain.DraftDecisionLogVO;
import com.ruoyi.postgrad.recommend.service.IDraftDecisionLogService;

/**
 * 草稿决策日志服务实现 —— Redis 存储。
 */
@Service
public class DraftDecisionLogServiceImpl implements IDraftDecisionLogService {

    private static final Logger log = LoggerFactory.getLogger(DraftDecisionLogServiceImpl.class);

    private static final String LOG_KEY_PREFIX = "ai:v2:decision-log:";
    private static final long TTL_DAYS = 7;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public void append(Long userId, DraftDecisionLogVO entry) {
        List<DraftDecisionLogVO> logs = listByUser(userId);
        logs.add(entry);
        redisTemplate.opsForValue().set(
            logKey(userId), JSON.toJSONString(logs), Duration.ofDays(TTL_DAYS));
    }

    @Override
    public List<DraftDecisionLogVO> listByUser(Long userId) {
        String json = redisTemplate.opsForValue().get(logKey(userId));
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return JSON.parseArray(json, DraftDecisionLogVO.class);
        } catch (Exception e) {
            log.warn("[DecisionLog] parse error userId={}: {}", userId, e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public void clear(Long userId) {
        redisTemplate.delete(logKey(userId));
    }

    private String logKey(Long userId) { return LOG_KEY_PREFIX + userId; }
}
