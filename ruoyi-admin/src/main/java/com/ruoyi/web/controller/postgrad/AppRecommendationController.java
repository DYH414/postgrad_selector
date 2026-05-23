package com.ruoyi.web.controller.postgrad;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.model.AppLoginUser;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.postgrad.domain.RecommendationRequest;
import com.ruoyi.postgrad.domain.RecommendationResult;
import com.ruoyi.postgrad.service.IRecommendationService;

@RestController
@RequestMapping("/app/recommendation")
public class AppRecommendationController
{
    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private IRecommendationService recommendationService;

    @PostMapping("/generate")
    public AjaxResult generate()
    {
        AppLoginUser loginUser = getCurrentAppUser();
        if (loginUser == null) return AjaxResult.error("未登录");

        Long userId = loginUser.getUserId();

        Map<String, Object> profile;
        try
        {
            profile = jdbc.queryForMap("SELECT * FROM user_profile WHERE user_id = ?", userId);
        }
        catch (Exception e)
        {
            return AjaxResult.error("请先完善考研画像");
        }

        if (profile == null || profile.get("estimated_score") == null)
        {
            return AjaxResult.error("请先完善考研画像");
        }

        RecommendationRequest req = buildRequest(profile);
        RecommendationResult result = recommendationService.generate(req);

        String profileSnapshot = JSON.toJSONString(profile);
        String resultJson = JSON.toJSONString(result);

        jdbc.update(
            "INSERT INTO recommendation_log (user_id, profile_snapshot, result_json, rule_version, created_at) " +
            "VALUES (?, ?, ?, 'v1', NOW())",
            userId, profileSnapshot, resultJson);

        return AjaxResult.success(result);
    }

    @PostMapping("/filter")
    public AjaxResult filter()
    {
        AppLoginUser loginUser = getCurrentAppUser();
        if (loginUser == null) return AjaxResult.error("未登录");

        Long userId = loginUser.getUserId();

        Map<String, Object> profile;
        try
        {
            profile = jdbc.queryForMap("SELECT * FROM user_profile WHERE user_id = ?", userId);
        }
        catch (Exception e)
        {
            return AjaxResult.error("请先完善考研画像");
        }

        if (profile == null || profile.get("estimated_score") == null)
            return AjaxResult.error("请先完善考研画像");

        RecommendationRequest req = buildFilterRequest(profile);
        RecommendationResult result = recommendationService.filter(req);
        return AjaxResult.success(result);
    }

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

    private RecommendationRequest buildRequest(Map<String, Object> profile)
    {
        RecommendationRequest req = buildBaseRequest(profile);
        req.setRiskPreference(getStr(profile, "risk_preference", "balanced"));
        req.setIncludeNotRecommended(true);
        req.setIncludeOverflow(true);
        return req;
    }

    private RecommendationRequest buildFilterRequest(Map<String, Object> profile)
    {
        RecommendationRequest req = buildBaseRequest(profile);
        Object sr = profile.get("score_range");
        req.setScoreRange(sr != null ? intVal(sr) : 20);
        return req;
    }

    private RecommendationRequest buildBaseRequest(Map<String, Object> profile)
    {
        RecommendationRequest req = new RecommendationRequest();
        req.setEstimatedScore(intVal(profile.get("estimated_score")));

        Object regions = profile.get("target_regions");
        if (regions instanceof String)
        {
            try { req.setTargetProvinces(JSON.parseArray((String) regions, String.class)); }
            catch (Exception e) { req.setTargetProvinces(new ArrayList<>()); }
        }

        Object directionKeys = profile.get("target_directions");
        if (directionKeys instanceof String)
        {
            try { req.setDirectionKeys(JSON.parseArray((String) directionKeys, String.class)); }
            catch (Exception e) { req.setDirectionKeys(new ArrayList<>()); }
        }

        req.setAcceptPartTime(intVal(profile.get("accept_part_time")) == 1);
        req.setAcceptAcademic(intVal(profile.get("accept_academic")) == 1);

        return req;
    }

    private static String getStr(Map<String, Object> map, String key, String def)
    {
        Object v = map.get(key);
        return v != null ? v.toString() : def;
    }

    private static int intVal(Object v)
    {
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof Boolean) return (Boolean) v ? 1 : 0;
        return 0;
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
