package com.ruoyi.web.controller.postgrad;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.model.AppLoginUser;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.postgrad.service.IAiRecommendationService;

@RestController
@RequestMapping("/app/ai-recommend")
public class AppAiRecommendationController {

    @Autowired
    private IAiRecommendationService aiService;

    @PostMapping("/start")
    public AjaxResult start(@RequestBody Map<String, Object> body) {
        AppLoginUser user = getCurrentAppUser();
        if (user == null) return AjaxResult.error("未登录");
        try {
            return AjaxResult.success(aiService.startConversation(user.getUserId(), body));
        } catch (Exception e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    @PostMapping("/chat")
    public AjaxResult chat(@RequestBody Map<String, Object> body) {
        AppLoginUser user = getCurrentAppUser();
        if (user == null) return AjaxResult.error("未登录");
        try {
            String conversationId = (String) body.get("conversationId");
            String message = (String) body.get("message");
            Map<String, Object> result = aiService.chat(user.getUserId(), conversationId, message);
            if (Boolean.TRUE.equals(result.get("fallback"))) {
                return AjaxResult.success("AI 对话暂不可用，已为你生成规则推荐结果", result);
            }
            return AjaxResult.success(result);
        } catch (SecurityException e) {
            return AjaxResult.error(403, "无权访问此对话");
        } catch (IllegalArgumentException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    @PostMapping("/generate-report")
    public AjaxResult generateReport(@RequestBody Map<String, Object> body) {
        AppLoginUser user = getCurrentAppUser();
        if (user == null) return AjaxResult.error("未登录");
        try {
            String conversationId = (String) body.get("conversationId");
            return AjaxResult.success(aiService.generateReport(user.getUserId(), conversationId));
        } catch (SecurityException e) {
            return AjaxResult.error(403, "无权访问");
        } catch (IllegalArgumentException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    @GetMapping("/report/{id}")
    public AjaxResult getReport(@PathVariable Long id) {
        AppLoginUser user = getCurrentAppUser();
        if (user == null) return AjaxResult.error("未登录");
        try {
            return AjaxResult.success(aiService.getReport(user.getUserId(), id));
        } catch (SecurityException e) {
            return AjaxResult.error(403, "无权访问");
        } catch (IllegalArgumentException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    @GetMapping("/reports")
    public AjaxResult getReports() {
        AppLoginUser user = getCurrentAppUser();
        if (user == null) return AjaxResult.error("未登录");
        return AjaxResult.success(aiService.getReports(user.getUserId()));
    }

    @PostMapping("/resume")
    public AjaxResult resume(@RequestBody Map<String, Object> body) {
        AppLoginUser user = getCurrentAppUser();
        if (user == null) return AjaxResult.error("未登录");
        try {
            String conversationId = (String) body.get("conversationId");
            return AjaxResult.success(aiService.resumeConversation(user.getUserId(), conversationId));
        } catch (SecurityException e) {
            return AjaxResult.error(403, "无权访问");
        }
    }

    @PostMapping("/analyze")
    public AjaxResult analyze() {
        AppLoginUser user = getCurrentAppUser();
        if (user == null) return AjaxResult.error("未登录");
        try {
            return AjaxResult.success(aiService.analyze(user.getUserId()));
        } catch (Exception e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    private AppLoginUser getCurrentAppUser() {
        try {
            Object principal = SecurityUtils.getAuthentication().getPrincipal();
            if (principal instanceof AppLoginUser) return (AppLoginUser) principal;
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
