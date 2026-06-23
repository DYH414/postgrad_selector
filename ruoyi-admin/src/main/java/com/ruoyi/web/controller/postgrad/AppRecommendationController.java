package com.ruoyi.web.controller.postgrad;

import java.util.LinkedHashMap;
import java.util.Map;
import com.ruoyi.common.annotation.RateLimiter;
import com.ruoyi.common.enums.LimitType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.model.AppLoginUser;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.postgrad.service.IProgramRecommendationService;

@RestController
@RequestMapping("/app/recommendation")
public class AppRecommendationController
{
    @Autowired
    private IProgramRecommendationService recommendationService;

    @Anonymous
    @GetMapping("/options")
    public AjaxResult options()
    {
        AppLoginUser loginUser = getCurrentAppUser();
        Long userId = loginUser == null ? null : loginUser.getUserId();
        return AjaxResult.success(recommendationService.recommendationOptions(userId));
    }

    @RateLimiter(count = 10, time = 60, limitType = LimitType.IP)
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
