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
import com.ruoyi.postgrad.domain.AdmissionScore;
import com.ruoyi.postgrad.service.IAdmissionScoreService;

@RestController
@RequestMapping("/postgrad/admissionScore")
public class AdmissionScoreController extends BaseController
{
    @Autowired
    private IAdmissionScoreService admissionScoreService;

    @GetMapping("/optionselect")
    public AjaxResult optionselect()
    {
        List<AdmissionScore> list = admissionScoreService.selectAdmissionScoreList(new AdmissionScore());
        return success(list);
    }

    @PreAuthorize("@ss.hasPermi('postgrad:admissionScore:list')")
    @GetMapping("/list")
    public TableDataInfo list(AdmissionScore admissionScore)
    {
        startPage();
        List<AdmissionScore> list = admissionScoreService.selectAdmissionScoreList(admissionScore);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('postgrad:admissionScore:query')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id)
    {
        return success(admissionScoreService.selectAdmissionScoreById(id));
    }

    @PreAuthorize("@ss.hasPermi('postgrad:admissionScore:add')")
    @Log(title = "AdmissionScore", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AdmissionScore admissionScore)
    {
        return toAjax(admissionScoreService.insertAdmissionScore(admissionScore));
    }

    @PreAuthorize("@ss.hasPermi('postgrad:admissionScore:edit')")
    @Log(title = "AdmissionScore", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AdmissionScore admissionScore)
    {
        return toAjax(admissionScoreService.updateAdmissionScore(admissionScore));
    }

    @PreAuthorize("@ss.hasPermi('postgrad:admissionScore:remove')")
    @Log(title = "AdmissionScore", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(admissionScoreService.deleteAdmissionScoreByIds(ids));
    }
}
