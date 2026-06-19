package com.ruoyi.postgrad.recommend.service;

import com.ruoyi.postgrad.recommend.domain.DraftVO;

public interface IDraftSummaryMessageService {
    /** Generate completion summary text. Returns null if draft has 0 candidates. */
    String generateSummary(DraftVO draft);
}
