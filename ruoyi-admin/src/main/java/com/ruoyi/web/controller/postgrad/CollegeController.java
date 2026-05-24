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
import com.ruoyi.postgrad.domain.College;
import com.ruoyi.postgrad.service.ICollegeService;

@RestController
@RequestMapping("/postgrad/college")
public class CollegeController extends BaseController
{
    @Autowired
    private ICollegeService collegeService;

    @GetMapping("/optionselect")
    public AjaxResult optionselect()
    {
        List<College> list = collegeService.selectCollegeList(new College());
        return success(list);
    }

    @PreAuthorize("@ss.hasPermi('postgrad:college:list')")
    @GetMapping("/list")
    public TableDataInfo list(College college)
    {
        startPage();
        List<College> list = collegeService.selectCollegeList(college);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('postgrad:college:query')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id)
    {
        return success(collegeService.selectCollegeById(id));
    }

    @PreAuthorize("@ss.hasPermi('postgrad:college:add')")
    @Log(title = "College", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody College college)
    {
        return toAjax(collegeService.insertCollege(college));
    }

    @PreAuthorize("@ss.hasPermi('postgrad:college:edit')")
    @Log(title = "College", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody College college)
    {
        return toAjax(collegeService.updateCollege(college));
    }

    @PreAuthorize("@ss.hasPermi('postgrad:college:remove')")
    @Log(title = "College", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(collegeService.deleteCollegeByIds(ids));
    }
}
