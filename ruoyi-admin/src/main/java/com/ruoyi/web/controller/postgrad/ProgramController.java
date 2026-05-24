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
import com.ruoyi.postgrad.domain.Program;
import com.ruoyi.postgrad.service.IProgramService;

@RestController
@RequestMapping("/postgrad/program")
public class ProgramController extends BaseController
{
    @Autowired
    private IProgramService programService;

    @GetMapping("/optionselect")
    public AjaxResult optionselect()
    {
        List<Program> list = programService.selectProgramList(new Program());
        return success(list);
    }

    @PreAuthorize("@ss.hasPermi('postgrad:program:list')")
    @GetMapping("/list")
    public TableDataInfo list(Program program)
    {
        startPage();
        List<Program> list = programService.selectProgramList(program);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('postgrad:program:query')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id)
    {
        return success(programService.selectProgramById(id));
    }

    @PreAuthorize("@ss.hasPermi('postgrad:program:add')")
    @Log(title = "Program", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody Program program)
    {
        return toAjax(programService.insertProgram(program));
    }

    @PreAuthorize("@ss.hasPermi('postgrad:program:edit')")
    @Log(title = "Program", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody Program program)
    {
        return toAjax(programService.updateProgram(program));
    }

    @PreAuthorize("@ss.hasPermi('postgrad:program:remove')")
    @Log(title = "Program", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(programService.deleteProgramByIds(ids));
    }
}
