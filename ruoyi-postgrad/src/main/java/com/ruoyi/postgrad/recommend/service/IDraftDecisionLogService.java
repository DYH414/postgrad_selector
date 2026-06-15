package com.ruoyi.postgrad.recommend.service;

import com.ruoyi.postgrad.recommend.domain.DraftDecisionLogVO;

import java.util.List;

/**
 * 草稿决策日志服务 —— 记录系统/AI/用户对草稿的每次变更。
 */
public interface IDraftDecisionLogService {

    /** 追加一条决策日志 */
    void append(Long userId, DraftDecisionLogVO entry);

    /** 读取用户当前决策日志 */
    List<DraftDecisionLogVO> listByUser(Long userId);

    /** 清除用户决策日志（生成新草稿时） */
    void clear(Long userId);
}
