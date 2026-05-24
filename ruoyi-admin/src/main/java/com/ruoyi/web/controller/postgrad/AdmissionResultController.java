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
import com.ruoyi.postgrad.domain.AdmissionResult;
import com.ruoyi.postgrad.service.IAdmissionResultService;

@RestController
@RequestMapping("/postgrad/admissionResult")
public class AdmissionResultController extends BaseController
{
    @Autowired
    private IAdmissionResultService admissionResultService;

    @GetMapping("/optionselect")
    public AjaxResult optionselect()
    {
        List<AdmissionResult> list = admissionResultService.selectAdmissionResultList(new AdmissionResult());
        return success(list);
    }

    @PreAuthorize("@ss.hasPermi('postgrad:admissionResult:list')")
    @GetMapping("/list")
    public TableDataInfo list(AdmissionResult admissionResult)
    {
        startPage();
        List<AdmissionResult> list = admissionResultService.selectAdmissionResultList(admissionResult);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('postgrad:admissionResult:query')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id)
    {
        return success(admissionResultService.selectAdmissionResultById(id));
    }

    @PreAuthorize("@ss.hasPermi('postgrad:admissionResult:add')")
    @Log(title = "AdmissionResult", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AdmissionResult admissionResult)
    {
        return toAjax(admissionResultService.insertAdmissionResult(admissionResult));
    }

    @PreAuthorize("@ss.hasPermi('postgrad:admissionResult:edit')")
    @Log(title = "AdmissionResult", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AdmissionResult admissionResult)
    {
        return toAjax(admissionResultService.updateAdmissionResult(admissionResult));
    }

    @PreAuthorize("@ss.hasPermi('postgrad:admissionResult:remove')")
    @Log(title = "AdmissionResult", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(admissionResultService.deleteAdmissionResultByIds(ids));
    }
}
