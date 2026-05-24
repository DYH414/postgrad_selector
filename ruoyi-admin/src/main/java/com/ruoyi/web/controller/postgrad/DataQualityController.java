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
import com.ruoyi.postgrad.domain.DataQuality;
import com.ruoyi.postgrad.service.IDataQualityService;

@RestController
@RequestMapping("/postgrad/programYearDataQuality")
public class DataQualityController extends BaseController
{
    @Autowired
    private IDataQualityService dataQualityService;

    @GetMapping("/optionselect")
    public AjaxResult optionselect()
    {
        List<DataQuality> list = dataQualityService.selectDataQualityList(new DataQuality());
        return success(list);
    }

    @PreAuthorize("@ss.hasPermi('postgrad:programYearDataQuality:list')")
    @GetMapping("/list")
    public TableDataInfo list(DataQuality dataQuality)
    {
        startPage();
        List<DataQuality> list = dataQualityService.selectDataQualityList(dataQuality);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('postgrad:programYearDataQuality:query')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id)
    {
        return success(dataQualityService.selectDataQualityById(id));
    }

    @PreAuthorize("@ss.hasPermi('postgrad:programYearDataQuality:add')")
    @Log(title = "DataQuality", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody DataQuality dataQuality)
    {
        return toAjax(dataQualityService.insertDataQuality(dataQuality));
    }

    @PreAuthorize("@ss.hasPermi('postgrad:programYearDataQuality:edit')")
    @Log(title = "DataQuality", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody DataQuality dataQuality)
    {
        return toAjax(dataQualityService.updateDataQuality(dataQuality));
    }

    @PreAuthorize("@ss.hasPermi('postgrad:programYearDataQuality:remove')")
    @Log(title = "DataQuality", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(dataQualityService.deleteDataQualityByIds(ids));
    }
}
