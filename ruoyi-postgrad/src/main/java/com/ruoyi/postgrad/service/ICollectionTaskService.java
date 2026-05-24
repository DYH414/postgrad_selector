package com.ruoyi.postgrad.service;

import java.util.List;
import com.ruoyi.postgrad.domain.CollectionTask;

public interface ICollectionTaskService {
    List<CollectionTask> selectCollectionTaskList(CollectionTask entity);
    CollectionTask selectCollectionTaskById(Long id);
    int insertCollectionTask(CollectionTask entity);
    int updateCollectionTask(CollectionTask entity);
    int deleteCollectionTaskById(Long id);
    int deleteCollectionTaskByIds(Long[] ids);
}
