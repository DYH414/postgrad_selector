package com.ruoyi.web.controller.postgrad;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.postgrad.domain.RecommendationRequest;
import com.ruoyi.postgrad.domain.RecommendationResult;
import com.ruoyi.postgrad.service.IRecommendationService;

/**
 * 考研择校推荐引擎
 */
@RestController
@RequestMapping("/postgrad/recommendation")
public class RecommendationController extends BaseController
{
    @Autowired
    private IRecommendationService recommendationService;

    /**
     * 生成个性化推荐
     */
    @PostMapping("/generate")
    @Log(title = "考研推荐", businessType = BusinessType.OTHER)
    public AjaxResult generate(@RequestBody RecommendationRequest request)
    {
        if (request.getEstimatedScore() < 100 || request.getEstimatedScore() > 500)
        {
            return error("预估分数不在有效范围(100-500)");
        }
        if (request.getTargetProvinces() == null || request.getTargetProvinces().isEmpty())
        {
            return error("请至少选择一个目标地区");
        }
        boolean hasProgramCodes = request.getProgramCodes() != null && !request.getProgramCodes().isEmpty();
        boolean hasDirectionKeys = request.getDirectionKeys() != null && !request.getDirectionKeys().isEmpty();
        if (!hasProgramCodes && !hasDirectionKeys)
        {
            return error("请至少选择一个目标专业方向");
        }

        RecommendationResult result = recommendationService.generate(request);
        return success(result);
    }
}
