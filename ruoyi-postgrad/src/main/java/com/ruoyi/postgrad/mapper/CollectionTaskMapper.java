package com.ruoyi.postgrad.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import com.ruoyi.postgrad.domain.CollectionTask;

@Mapper
public interface CollectionTaskMapper {
    List<CollectionTask> selectCollectionTaskList(CollectionTask entity);
    CollectionTask selectCollectionTaskById(Long id);
    int insertCollectionTask(CollectionTask entity);
    int updateCollectionTask(CollectionTask entity);
    int deleteCollectionTaskById(Long id);
    int deleteCollectionTaskByIds(Long[] ids);
}
