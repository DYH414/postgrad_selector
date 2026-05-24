package com.ruoyi.web.controller.postgrad;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.postgrad.domain.AdmissionPlan;
import com.ruoyi.postgrad.service.IAdmissionPlanService;

@RestController
@RequestMapping("/postgrad/admissionPlan")
public class AdmissionPlanController extends BaseController
{
    @Autowired
    private IAdmissionPlanService admissionPlanService;

    @GetMapping("/optionselect")
    public AjaxResult optionselect()
    {
        List<AdmissionPlan> list = admissionPlanService.selectAdmissionPlanList(new AdmissionPlan());
        return success(list);
    }

    @PreAuthorize("@ss.hasPermi('postgrad:admissionPlan:list')")
    @GetMapping("/list")
    public TableDataInfo list(AdmissionPlan admissionPlan)
    {
        startPage();
        List<AdmissionPlan> list = admissionPlanService.selectAdmissionPlanList(admissionPlan);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('postgrad:admissionPlan:query')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id)
    {
        return success(admissionPlanService.selectAdmissionPlanById(id));
    }

    @PreAuthorize("@ss.hasPermi('postgrad:admissionPlan:add')")
    @Log(title = "AdmissionPlan", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AdmissionPlan admissionPlan)
    {
        return toAjax(admissionPlanService.insertAdmissionPlan(admissionPlan));
    }

    @PreAuthorize("@ss.hasPermi('postgrad:admissionPlan:edit')")
    @Log(title = "AdmissionPlan", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AdmissionPlan admissionPlan)
    {
        return toAjax(admissionPlanService.updateAdmissionPlan(admissionPlan));
    }

    @PreAuthorize("@ss.hasPermi('postgrad:admissionPlan:remove')")
    @Log(title = "AdmissionPlan", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(admissionPlanService.deleteAdmissionPlanByIds(ids));
    }
}
