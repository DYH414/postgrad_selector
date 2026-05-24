package com.ruoyi.web.controller.postgrad;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.model.AppLoginUser;
import com.ruoyi.common.utils.SecurityUtils;

/**
 * App 端 — 推荐历史（推荐引擎已移除，待重写）
 */
@RestController
@RequestMapping("/app/recommendation")
public class AppRecommendationController
{
    @Autowired
    private JdbcTemplate jdbc;

    @GetMapping("/history")
    public AjaxResult history()
    {
        AppLoginUser loginUser = getCurrentAppUser();
        if (loginUser == null) return AjaxResult.error("未登录");

        List<Map<String, Object>> logs = jdbc.queryForList(
            "SELECT id, rule_version, data_version, is_paid, created_at " +
            "FROM recommendation_log WHERE user_id = ? ORDER BY created_at DESC LIMIT 50",
            loginUser.getUserId());

        return AjaxResult.success(logs);
    }

    @GetMapping("/history/{id}")
    public AjaxResult historyDetail(@PathVariable Long id)
    {
        AppLoginUser loginUser = getCurrentAppUser();
        if (loginUser == null) return AjaxResult.error("未登录");

        Map<String, Object> log;
        try
        {
            log = jdbc.queryForMap(
                "SELECT * FROM recommendation_log WHERE id = ? AND user_id = ?",
                id, loginUser.getUserId());
        }
        catch (Exception e)
        {
            return AjaxResult.error("记录不存在");
        }

        if (log.get("profile_snapshot") != null)
        {
            log.put("profile", JSON.parse((String) log.get("profile_snapshot")));
        }
        if (log.get("result_json") != null)
        {
            log.put("result", JSON.parse((String) log.get("result_json")));
        }

        return AjaxResult.success(log);
    }

    private AppLoginUser getCurrentAppUser()
    {
        try
        {
            Object principal = SecurityUtils.getAuthentication().getPrincipal();
            if (principal instanceof AppLoginUser) return (AppLoginUser) principal;
            return null;
        }
        catch (Exception e)
        {
            return null;
        }
    }
}
