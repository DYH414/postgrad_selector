package com.ruoyi.web.controller.postgrad;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.model.AppLoginUser;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.postgrad.service.IProgramRecommendationService;
import com.ruoyi.postgrad.service.IRecommendationLogService;

@RestController
@RequestMapping("/app/recommendation")
public class AppRecommendationController
{
    @Autowired
    private IProgramRecommendationService recommendationService;

    @Autowired
    private IRecommendationLogService logService;

    @Anonymous
    @GetMapping("/options")
    public AjaxResult options()
    {
        AppLoginUser loginUser = getCurrentAppUser();
        Long userId = loginUser == null ? null : loginUser.getUserId();
        return AjaxResult.success(recommendationService.recommendationOptions(userId));
    }

    @PostMapping("/generate")
    public AjaxResult generate(@RequestBody Map<String, Object> body)
    {
        AppLoginUser loginUser = getCurrentAppUser();
        if (loginUser == null) return AjaxResult.error("未登录");

        try
        {
            return AjaxResult.success(recommendationService.generateRecommendation(loginUser.getUserId(), body));
        }
        catch (IllegalArgumentException e)
        {
            return AjaxResult.error(e.getMessage());
        }
    }

    @GetMapping("/result/{id}")
    public AjaxResult result(@PathVariable Long id)
    {
        AppLoginUser loginUser = getCurrentAppUser();
        if (loginUser == null) return AjaxResult.error("未登录");

        try
        {
            return AjaxResult.success(recommendationService.loadRecommendationResult(loginUser.getUserId(), id));
        }
        catch (Exception e)
        {
            return AjaxResult.error("推荐结果不存在");
        }
    }

    @GetMapping("/history")
    public AjaxResult history()
    {
        AppLoginUser loginUser = getCurrentAppUser();
        if (loginUser == null) return AjaxResult.error("未登录");

        List<Map<String, Object>> logs = logService.selectLogListByUserId(loginUser.getUserId());
        return AjaxResult.success(normalizeHistoryList(logs));
    }

    @GetMapping("/history/{id}")
    public AjaxResult historyDetail(@PathVariable Long id)
    {
        AppLoginUser loginUser = getCurrentAppUser();
        if (loginUser == null) return AjaxResult.error("未登录");

        Map<String, Object> log = logService.selectLogByIdAndUserId(id, loginUser.getUserId());
        if (log == null) return AjaxResult.error("记录不存在");

        return AjaxResult.success(normalizeHistoryDetail(log));
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

    private List<Map<String, Object>> normalizeHistoryList(List<Map<String, Object>> logs)
    {
        if (logs == null) return new ArrayList<>();
        return logs.stream().map(this::normalizeHistorySummary).collect(Collectors.toList());
    }

    private Map<String, Object> normalizeHistorySummary(Map<String, Object> log)
    {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", log.get("id"));
        item.put("ruleVersion", log.get("rule_version"));
        item.put("dataVersion", log.get("data_version"));
        item.put("isPaid", log.get("is_paid"));
        item.put("createdAt", log.get("created_at"));
        return item;
    }

    private Map<String, Object> normalizeHistoryDetail(Map<String, Object> log)
    {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", log.get("id"));
        item.put("userId", log.get("user_id"));
        item.put("ruleVersion", log.get("rule_version"));
        item.put("dataVersion", log.get("data_version"));
        item.put("isPaid", log.get("is_paid"));
        item.put("createdAt", log.get("created_at"));
        item.put("profileSnapshot", log.get("profile_snapshot"));
        item.put("resultJson", log.get("result_json"));
        if (log.get("profile_snapshot") != null)
            item.put("profile", JSON.parse(String.valueOf(log.get("profile_snapshot"))));
        if (log.get("result_json") != null)
            item.put("result", JSON.parse(String.valueOf(log.get("result_json"))));
        return item;
    }
}
