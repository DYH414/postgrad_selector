package com.ruoyi.postgrad.service.impl;

import com.ruoyi.postgrad.domain.ai.AiReportSupport;
import com.ruoyi.postgrad.service.IAiVerificationService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class NoopAiVerificationServiceImpl implements IAiVerificationService {
    @Override
    public Map<String, Object> verify(Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        boolean hasProgramId = input != null && input.get("programId") != null;
        result.put("verificationStatus", hasProgramId
            ? AiReportSupport.STATUS_LOCAL_DATA_ONLY
            : AiReportSupport.STATUS_PENDING);
        result.put("verificationProvider", null);
        result.put("sourceTitle", null);
        result.put("sourceUrl", null);
        result.put("summary", hasProgramId ? "Phase 1 未启用联网核验，使用本地数据。" : "本地数据不足，等待核验。");
        return result;
    }
}
