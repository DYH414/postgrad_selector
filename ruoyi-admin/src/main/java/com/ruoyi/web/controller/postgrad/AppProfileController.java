package com.ruoyi.web.controller.postgrad;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.model.AppLoginUser;
import com.ruoyi.common.utils.SecurityUtils;

@RestController
@RequestMapping("/app/profile")
public class AppProfileController
{
    @Autowired
    private JdbcTemplate jdbc;

    @GetMapping
    public AjaxResult get()
    {
        AppLoginUser loginUser = getCurrentAppUser();
        if (loginUser == null)
        {
            return AjaxResult.error("未登录");
        }
        Long userId = loginUser.getUserId();
        try
        {
            Map<String, Object> profile = jdbc.queryForMap(
                "SELECT * FROM user_profile WHERE user_id = ?", userId);
            return AjaxResult.success(profile);
        }
        catch (Exception e)
        {
            return AjaxResult.success(null);
        }
    }

    @PostMapping
    public AjaxResult save(@RequestBody Map<String, Object> body)
    {
        AppLoginUser loginUser = getCurrentAppUser();
        if (loginUser == null)
        {
            return AjaxResult.error("未登录");
        }
        Long userId = loginUser.getUserId();

        jdbc.update("DELETE FROM user_profile WHERE user_id = ?", userId);

        jdbc.update(
            "INSERT INTO user_profile (user_id, estimated_score, target_regions, accept_part_time, " +
            "accept_transfer, accept_academic, accept_joint, risk_preference, undergrad_tier, " +
            "undergraduate_major, is_cross_major, math_level, english_level, cs_level, " +
            "daily_study_hours, review_progress, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
            userId,
            body.get("estimatedScore"),
            body.get("targetRegions"),
            boolVal(body.get("acceptPartTime")),
            boolVal(body.get("acceptTransfer")),
            boolVal(body.get("acceptAcademic")),
            boolVal(body.get("acceptJoint")),
            body.get("riskPreference"),
            body.get("undergradTier"),
            body.get("undergraduateMajor"),
            boolVal(body.get("isCrossMajor")),
            body.get("mathLevel"),
            body.get("englishLevel"),
            body.get("csLevel"),
            body.get("dailyStudyHours"),
            body.get("reviewProgress")
        );

        return AjaxResult.success("保存成功");
    }

    private int boolVal(Object v)
    {
        if (v instanceof Boolean) return (Boolean) v ? 1 : 0;
        if (v instanceof Number) return ((Number) v).intValue() != 0 ? 1 : 0;
        if (v instanceof String) return "true".equalsIgnoreCase((String) v) ? 1 : 0;
        return 0;
    }

    private AppLoginUser getCurrentAppUser()
    {
        try
        {
            Object principal = SecurityUtils.getAuthentication().getPrincipal();
            if (principal instanceof AppLoginUser)
            {
                return (AppLoginUser) principal;
            }
            return null;
        }
        catch (Exception e)
        {
            return null;
        }
    }
}
