package com.ruoyi.web.controller.postgrad;

import java.util.Map;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.postgrad.service.IWorkspaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/postgrad/workspace")
public class WorkspaceController extends BaseController
{
    @Autowired
    private IWorkspaceService workspaceService;

    @PreAuthorize("@ss.hasPermi('postgrad:workspace:view')")
    @GetMapping("/stats")
    public AjaxResult stats(@RequestParam Map<String, String> params)
    {
        return success(workspaceService.selectWorkspaceStats(params));
    }

    @PreAuthorize("@ss.hasPermi('postgrad:workspace:view')")
    @GetMapping("/schools")
    public AjaxResult schools(@RequestParam Map<String, String> params)
    {
        return success(workspaceService.selectWorkspaceSchools(params));
    }

    @PreAuthorize("@ss.hasPermi('postgrad:workspace:view')")
    @GetMapping("/school/{id}")
    public AjaxResult schoolWorkspace(@PathVariable("id") Long id, @RequestParam Map<String, String> params)
    {
        return success(workspaceService.selectSchoolWorkspace(id, params));
    }
}
