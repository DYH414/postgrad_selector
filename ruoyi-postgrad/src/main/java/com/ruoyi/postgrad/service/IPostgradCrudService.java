package com.ruoyi.postgrad.service;

import java.util.List;
import java.util.Map;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;

/**
 * 通用CRUD Service接口 —— 元数据驱动的动态业务模块。
 *
 * @author ruoyi
 */
public interface IPostgradCrudService
{
    /** 分页列表查询 */
    TableDataInfo list(String module, Map<String, String> params);

    /** CSV导出 */
    List<Map<String, Object>> export(String module, Map<String, String> params);

    /** 按主键查询 */
    Map<String, Object> getInfo(String module, String id);

    /** 选项列表（下拉框） */
    List<Map<String, Object>> optionselect(String module, Map<String, String> params);

    /** 新增记录 */
    int add(String module, Map<String, Object> body);

    /** 修改记录 */
    int edit(String module, Map<String, Object> body);

    /** 删除记录（支持逗号分隔的多个ID） */
    int remove(String module, String ids);
}
