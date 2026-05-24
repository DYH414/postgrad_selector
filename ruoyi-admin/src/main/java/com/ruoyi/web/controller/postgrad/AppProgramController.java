package com.ruoyi.web.controller.postgrad;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.domain.AjaxResult;

@Anonymous
@RestController
@RequestMapping("/app/programs")
public class AppProgramController
{
    @Autowired
    private AppProgramAggregateService aggregateService;

    @GetMapping("/{programId}/detail")
    public AjaxResult detail(@PathVariable Long programId, @RequestParam(required = false) Integer estimatedScore)
    {
        try
        {
            return AjaxResult.success(aggregateService.programDetail(programId, estimatedScore));
        }
        catch (Exception e)
        {
            return AjaxResult.error("专业不存在或暂无可用数据");
        }
    }

    @GetMapping("/compare")
    public AjaxResult compare(@RequestParam String programIds, @RequestParam(required = false) Integer estimatedScore)
    {
        List<Long> ids;
        try
        {
            ids = Arrays.stream(programIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .collect(Collectors.toList());
        }
        catch (NumberFormatException e)
        {
            return AjaxResult.error("对比项目 ID 格式不正确");
        }
        if (ids.isEmpty())
        {
            return AjaxResult.error("请选择要对比的院校专业");
        }
        if (ids.size() > 8)
        {
            return AjaxResult.error("一次最多对比 8 个项目");
        }
        return AjaxResult.success(aggregateService.comparePrograms(ids, estimatedScore));
    }
}
