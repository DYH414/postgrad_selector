package com.ruoyi.postgrad.observe.llm.service;

import com.ruoyi.postgrad.observe.llm.mapper.AiLlmCallTraceMapper;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class AiLlmTraceRetentionService {
    private static final Logger log = LoggerFactory.getLogger(AiLlmTraceRetentionService.class);
    private final AiLlmCallTraceMapper mapper;
    private final int retentionDays;

    public AiLlmTraceRetentionService(AiLlmCallTraceMapper mapper,
                                      @Value("${app.ai.observe.retention-days:30}") int retentionDays) {
        this.mapper = mapper;
        this.retentionDays = Math.max(1, retentionDays);
    }

    @Scheduled(cron = "${app.ai.observe.cleanup-cron:0 20 3 * * ?}")
    public void cleanupExpired() {
        cleanup(LocalDateTime.now());
    }

    void cleanup(LocalDateTime now) {
        try {
            int deleted = mapper.deleteBefore(now.minusDays(retentionDays));
            if (deleted > 0) log.info("[LlmTrace] Deleted {} expired records", deleted);
        } catch (Exception ex) {
            log.warn("[LlmTrace] Cleanup failed error={}", ex.getClass().getSimpleName());
        }
    }
}
