package com.ruoyi.web.controller.postgrad;

import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.model.AppLoginUser;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.postgrad.domain.UserProfile;
import com.ruoyi.postgrad.service.IUserProfileService;

@RestController
@RequestMapping("/app/profile")
public class AppProfileController
{
    @Autowired
    private IUserProfileService profileService;

    private static final ObjectMapper JSON = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private static final Map<String, Set<String>> ENUMS = Map.of(
            "riskPreference", Set.of("conservative", "balanced", "aggressive", "safe_first", "reach_first"),
            "schoolTierPreference", Set.of("must_211_or_better", "prefer_211_or_better", "no_strict_requirement", "developed_region_priority", "school_tier_priority", "safe_admission_priority", "tier_priority"),
            "regionStrategy", Set.of("developed_priority", "developed_balanced", "no_strict_requirement", "target_regions_only"),
            "undergradTier", Set.of("985", "211", "DOUBLE_FIRST", "PUBLIC_REGULAR", "PRIVATE", "JUNIOR_COLLEGE", "OTHER"),
            "mathLevel", Set.of("weak", "medium", "strong"),
            "englishLevel", Set.of("weak", "medium", "strong"),
            "csLevel", Set.of("weak", "medium", "strong"));

    @GetMapping
    public AjaxResult get()
    {
        AppLoginUser loginUser = getCurrentAppUser();
        if (loginUser == null) return AjaxResult.error("未登录");

        UserProfile profile = profileService.selectUserProfileByUserId(loginUser.getUserId());
        return AjaxResult.success(profile);
    }

    @PostMapping
    public ResponseEntity<AjaxResult> save(@RequestBody Map<String, Object> body)
    {
        AppLoginUser loginUser = getCurrentAppUser();
        if (loginUser == null) return ResponseEntity.ok(AjaxResult.error("未登录"));

        String regions;
        try
        {
            regions = parseRegions(body.get("targetRegions"));
        }
        catch (IllegalArgumentException e)
        {
            return ResponseEntity.badRequest().body(AjaxResult.error(400, e.getMessage()));
        }
        for (Map.Entry<String, Set<String>> entry : ENUMS.entrySet())
        {
            String value = stringVal(body.get(entry.getKey()));
            if (value != null && !entry.getValue().contains(value))
                return ResponseEntity.badRequest().body(AjaxResult.error(400, entry.getKey() + " is invalid"));
        }

        UserProfile profile = new UserProfile();
        profile.setEstimatedScore(intVal(body.get("estimatedScore")));
        profile.setExamCombo(stringVal(body.get("examCombo")));
        profile.setTargetRegions(regions);
        profile.setAcceptPartTime(intVal(body.get("acceptPartTime")));
        profile.setAcceptTransfer(intVal(body.get("acceptTransfer")));
        profile.setAcceptAcademic(intVal(body.get("acceptAcademic")));
        profile.setAcceptJoint(intVal(body.get("acceptJoint")));
        profile.setRiskPreference(normalizeRiskPreference(stringVal(body.get("riskPreference"))));
        profile.setSchoolTierPreference(normalizeSchoolTier(stringVal(body.get("schoolTierPreference"))));
        profile.setRegionStrategy(normalizeRegion(stringVal(body.get("regionStrategy"))));
        profile.setUndergradTier(stringVal(body.get("undergradTier")));
        profile.setUndergraduateMajor(stringVal(body.get("undergraduateMajor")));
        profile.setIsCrossMajor(intVal(body.get("isCrossMajor")));
        profile.setMathLevel(stringVal(body.get("mathLevel")));
        profile.setEnglishLevel(stringVal(body.get("englishLevel")));
        profile.setCsLevel(stringVal(body.get("csLevel")));
        if (body.get("dailyStudyHours") instanceof Number)
        {
            profile.setDailyStudyHours(java.math.BigDecimal.valueOf(((Number) body.get("dailyStudyHours")).doubleValue()));
        }
        profile.setReviewProgress(stringVal(body.get("reviewProgress")));

        profileService.saveOrUpdateUserProfile(loginUser.getUserId(), profile);
        return ResponseEntity.ok(AjaxResult.success("保存成功"));
    }

    private String parseRegions(Object value)
    {
        if (value == null || value instanceof String && ((String) value).isBlank()) return null;
        JsonNode node;
        try
        {
            node = value instanceof String ? JSON.readTree((String) value) : JSON.valueToTree(value);
        }
        catch (JsonProcessingException e)
        {
            throw new IllegalArgumentException("目标地区必须是字符串数组");
        }
        if (node == null || !node.isArray()) throw new IllegalArgumentException("目标地区必须是字符串数组");
        for (JsonNode item : node)
            if (!item.isTextual()) throw new IllegalArgumentException("目标地区必须是字符串数组");
        try
        {
            return JSON.writeValueAsString(node);
        }
        catch (JsonProcessingException e)
        {
            throw new IllegalArgumentException("目标地区必须是字符串数组");
        }
    }

    private AppLoginUser getCurrentAppUser()
    {
        try
        {
            Authentication auth = SecurityUtils.getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof AppLoginUser)
            {
                return (AppLoginUser) auth.getPrincipal();
            }
            return null;
        }
        catch (Exception e) { return null; }
    }

    private Integer intVal(Object val)
    {
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).intValue();
        try { return Integer.parseInt(String.valueOf(val)); } catch (Exception e) { return null; }
    }

    private String stringVal(Object val)
    {
        if (val == null) return null;
        String s = String.valueOf(val);
        return s.isBlank() ? null : s;
    }

    private static String normalizeRiskPreference(String v) {
        if (v == null) return "balanced";
        return switch (v) {
            case "conservative" -> "safe_first";
            case "aggressive" -> "reach_first";
            default -> v;
        };
    }

    private static String normalizeSchoolTier(String v) {
        if (v == null) return "no_strict_requirement";
        if ("must_211_or_better".equals(v)) return "tier_priority";
        return v;
    }

    private static String normalizeRegion(String v) {
        if (v == null) return "no_strict_requirement";
        if ("developed_balanced".equals(v)) return "developed_priority";
        return v;
    }
}
