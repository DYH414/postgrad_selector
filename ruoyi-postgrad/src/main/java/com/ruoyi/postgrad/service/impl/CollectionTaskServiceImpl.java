package com.ruoyi.postgrad.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.postgrad.domain.CollectionTask;
import com.ruoyi.postgrad.mapper.CollectionTaskMapper;
import com.ruoyi.postgrad.service.ICollectionTaskService;

@Service
public class CollectionTaskServiceImpl implements ICollectionTaskService {
    @Autowired
    private CollectionTaskMapper mapper;

    @Override
    public List<CollectionTask> selectCollectionTaskList(CollectionTask entity) { return mapper.selectCollectionTaskList(entity); }

    @Override
    public CollectionTask selectCollectionTaskById(Long id) { return mapper.selectCollectionTaskById(id); }

    @Override
    public int insertCollectionTask(CollectionTask entity) { return mapper.insertCollectionTask(entity); }

    @Override
    public int updateCollectionTask(CollectionTask entity) { return mapper.updateCollectionTask(entity); }

    @Override
    public int deleteCollectionTaskById(Long id) { return mapper.deleteCollectionTaskById(id); }

    @Override
    public int deleteCollectionTaskByIds(Long[] ids) { return mapper.deleteCollectionTaskByIds(ids); }
}
