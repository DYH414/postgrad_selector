package com.ruoyi.postgrad.service;

import java.util.List;
import java.util.Map;

/**
 * 数据审核Service接口
 */
public interface IReviewService
{
    /** 待审核列表 */
    Map<String, Object> list(Map<String, String> params, int pageNum, int pageSize);

    /** 记录详情 */
    Map<String, Object> detail(Long id);

    /** 通过并迁移 */
    void approve(Long id, String note);

    /** 驳回 */
    void reject(Long id, String note);

    /** 跳过 */
    void skip(Long id);

    /** 批量通过 */
    void batchApprove(List<Integer> ids);

    /** 审核统计 */
    Map<String, Object> stats();

    /** 一键通过学校/学院/专业目录数据（无分数、无计划、无录取数据） */
    int autoApproveDirectory();
}
