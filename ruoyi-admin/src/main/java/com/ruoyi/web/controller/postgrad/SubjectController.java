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
import com.ruoyi.postgrad.domain.Subject;
import com.ruoyi.postgrad.service.ISubjectService;

@RestController
@RequestMapping("/postgrad/subject")
public class SubjectController extends BaseController
{
    @Autowired
    private ISubjectService subjectService;

    @GetMapping("/optionselect")
    public AjaxResult optionselect()
    {
        List<Subject> list = subjectService.selectSubjectList(new Subject());
        return success(list);
    }

    @PreAuthorize("@ss.hasPermi('postgrad:subject:list')")
    @GetMapping("/list")
    public TableDataInfo list(Subject subject)
    {
        startPage();
        List<Subject> list = subjectService.selectSubjectList(subject);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('postgrad:subject:query')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id)
    {
        return success(subjectService.selectSubjectById(id));
    }

    @PreAuthorize("@ss.hasPermi('postgrad:subject:add')")
    @Log(title = "Subject", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody Subject subject)
    {
        return toAjax(subjectService.insertSubject(subject));
    }

    @PreAuthorize("@ss.hasPermi('postgrad:subject:edit')")
    @Log(title = "Subject", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody Subject subject)
    {
        return toAjax(subjectService.updateSubject(subject));
    }

    @PreAuthorize("@ss.hasPermi('postgrad:subject:remove')")
    @Log(title = "Subject", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(subjectService.deleteSubjectByIds(ids));
    }
}
