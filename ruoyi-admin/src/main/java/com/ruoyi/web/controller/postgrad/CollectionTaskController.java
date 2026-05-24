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
import com.ruoyi.postgrad.domain.CollectionTask;
import com.ruoyi.postgrad.service.ICollectionTaskService;

@RestController
@RequestMapping("/postgrad/dataCollectionTask")
public class CollectionTaskController extends BaseController
{
    @Autowired
    private ICollectionTaskService collectionTaskService;

    @GetMapping("/optionselect")
    public AjaxResult optionselect()
    {
        List<CollectionTask> list = collectionTaskService.selectCollectionTaskList(new CollectionTask());
        return success(list);
    }

    @PreAuthorize("@ss.hasPermi('postgrad:dataCollectionTask:list')")
    @GetMapping("/list")
    public TableDataInfo list(CollectionTask collectionTask)
    {
        startPage();
        List<CollectionTask> list = collectionTaskService.selectCollectionTaskList(collectionTask);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('postgrad:dataCollectionTask:query')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id)
    {
        return success(collectionTaskService.selectCollectionTaskById(id));
    }

    @PreAuthorize("@ss.hasPermi('postgrad:dataCollectionTask:add')")
    @Log(title = "CollectionTask", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody CollectionTask collectionTask)
    {
        return toAjax(collectionTaskService.insertCollectionTask(collectionTask));
    }

    @PreAuthorize("@ss.hasPermi('postgrad:dataCollectionTask:edit')")
    @Log(title = "CollectionTask", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody CollectionTask collectionTask)
    {
        return toAjax(collectionTaskService.updateCollectionTask(collectionTask));
    }

    @PreAuthorize("@ss.hasPermi('postgrad:dataCollectionTask:remove')")
    @Log(title = "CollectionTask", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(collectionTaskService.deleteCollectionTaskByIds(ids));
    }
}
