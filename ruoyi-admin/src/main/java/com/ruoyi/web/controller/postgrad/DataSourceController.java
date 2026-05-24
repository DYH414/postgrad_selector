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
import com.ruoyi.postgrad.domain.DataSource;
import com.ruoyi.postgrad.service.IDataSourceService;

@RestController
@RequestMapping("/postgrad/dataSource")
public class DataSourceController extends BaseController
{
    @Autowired
    private IDataSourceService dataSourceService;

    @GetMapping("/optionselect")
    public AjaxResult optionselect()
    {
        List<DataSource> list = dataSourceService.selectDataSourceList(new DataSource());
        return success(list);
    }

    @PreAuthorize("@ss.hasPermi('postgrad:dataSource:list')")
    @GetMapping("/list")
    public TableDataInfo list(DataSource dataSource)
    {
        startPage();
        List<DataSource> list = dataSourceService.selectDataSourceList(dataSource);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('postgrad:dataSource:query')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id)
    {
        return success(dataSourceService.selectDataSourceById(id));
    }

    @PreAuthorize("@ss.hasPermi('postgrad:dataSource:add')")
    @Log(title = "DataSource", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody DataSource dataSource)
    {
        return toAjax(dataSourceService.insertDataSource(dataSource));
    }

    @PreAuthorize("@ss.hasPermi('postgrad:dataSource:edit')")
    @Log(title = "DataSource", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody DataSource dataSource)
    {
        return toAjax(dataSourceService.updateDataSource(dataSource));
    }

    @PreAuthorize("@ss.hasPermi('postgrad:dataSource:remove')")
    @Log(title = "DataSource", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(dataSourceService.deleteDataSourceByIds(ids));
    }
}
