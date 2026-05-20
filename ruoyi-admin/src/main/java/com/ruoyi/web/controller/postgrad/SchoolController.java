package com.ruoyi.web.controller.postgrad;

import java.util.List;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.postgrad.domain.School;
import com.ruoyi.postgrad.service.ISchoolService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

/**
 * 学校基础信息Controller
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/postgrad/school")
public class SchoolController extends BaseController
{
    @Autowired
    private ISchoolService schoolService;

    /**
     * 查询学校基础信息列表
     */
    @PreAuthorize("@ss.hasPermi('postgrad:school:list')")
    @GetMapping("/list")
    public TableDataInfo list(School school)
    {
        startPage();
        List<School> list = schoolService.selectSchoolList(school);
        return getDataTable(list);
    }

    /**
     * 导出学校基础信息列表
     */
    @PreAuthorize("@ss.hasPermi('postgrad:school:export')")
    @Log(title = "学校管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, School school)
    {
        List<School> list = schoolService.selectSchoolList(school);
        ExcelUtil<School> util = new ExcelUtil<School>(School.class);
        util.exportExcel(response, list, "学校基础信息数据");
    }

    /**
     * 获取学校基础信息详细信息
     */
    @PreAuthorize("@ss.hasPermi('postgrad:school:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(schoolService.selectSchoolById(id));
    }

    /**
     * 获取学校数据概览。
     */
    @PreAuthorize("@ss.hasPermi('postgrad:school:query')")
    @GetMapping(value = "/{id}/overview")
    public AjaxResult overview(@PathVariable("id") Long id)
    {
        return success(schoolService.selectSchoolOverview(id));
    }

    /**
     * 获取学校选择框列表
     */
    @GetMapping("/optionselect")
    public AjaxResult optionselect(@RequestParam(required = false) String keyword)
    {
        if (StringUtils.hasText(keyword))
        {
            School school = new School();
            school.setName(keyword);
            return success(schoolService.selectSchoolList(school));
        }
        return success(schoolService.selectSchoolAll());
    }

    /**
     * 新增学校基础信息
     */
    @PreAuthorize("@ss.hasPermi('postgrad:school:add')")
    @Log(title = "学校管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody School school)
    {
        if (!schoolService.checkSchoolNameUnique(school))
        {
            return error("新增学校'" + school.getName() + "'失败，学校名称已存在");
        }
        return toAjax(schoolService.insertSchool(school));
    }

    /**
     * 修改学校基础信息
     */
    @PreAuthorize("@ss.hasPermi('postgrad:school:edit')")
    @Log(title = "学校管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody School school)
    {
        if (!schoolService.checkSchoolNameUnique(school))
        {
            return error("修改学校'" + school.getName() + "'失败，学校名称已存在");
        }
        return toAjax(schoolService.updateSchool(school));
    }

    /**
     * 删除学校基础信息
     */
    @PreAuthorize("@ss.hasPermi('postgrad:school:remove')")
    @Log(title = "学校管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(schoolService.deleteSchoolByIds(ids));
    }
}
