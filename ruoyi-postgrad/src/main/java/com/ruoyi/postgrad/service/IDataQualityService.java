package com.ruoyi.postgrad.service;

import java.util.List;
import com.ruoyi.postgrad.domain.DataQuality;

public interface IDataQualityService {
    List<DataQuality> selectDataQualityList(DataQuality entity);
    DataQuality selectDataQualityById(Long id);
    int insertDataQuality(DataQuality entity);
    int updateDataQuality(DataQuality entity);
    int deleteDataQualityById(Long id);
    int deleteDataQualityByIds(Long[] ids);
}
