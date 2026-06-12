package com.ruoyi.postgrad.recommend.service;

import com.ruoyi.postgrad.recommend.domain.DraftGenerationTaskState;
import com.ruoyi.postgrad.recommend.domain.DraftGenerationTaskVO;

public interface IDraftGenerationTaskService {
    DraftGenerationTaskVO start(Long userId);

    DraftGenerationTaskState getState(String taskId);

    boolean validateStreamToken(String taskId, String streamToken);
}
