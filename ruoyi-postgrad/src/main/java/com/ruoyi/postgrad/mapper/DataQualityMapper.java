package com.ruoyi.postgrad.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import com.ruoyi.postgrad.domain.DataQuality;

@Mapper
public interface DataQualityMapper {
    List<DataQuality> selectDataQualityList(DataQuality entity);
    DataQuality selectDataQualityById(Long id);
    int insertDataQuality(DataQuality entity);
    int updateDataQuality(DataQuality entity);
    int deleteDataQualityById(Long id);
    int deleteDataQualityByIds(Long[] ids);
}
