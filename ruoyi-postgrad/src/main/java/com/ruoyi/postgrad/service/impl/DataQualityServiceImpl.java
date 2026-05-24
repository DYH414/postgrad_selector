package com.ruoyi.postgrad.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.postgrad.domain.DataQuality;
import com.ruoyi.postgrad.mapper.DataQualityMapper;
import com.ruoyi.postgrad.service.IDataQualityService;

@Service
public class DataQualityServiceImpl implements IDataQualityService {
    @Autowired
    private DataQualityMapper mapper;

    @Override
    public List<DataQuality> selectDataQualityList(DataQuality entity) { return mapper.selectDataQualityList(entity); }

    @Override
    public DataQuality selectDataQualityById(Long id) { return mapper.selectDataQualityById(id); }

    @Override
    public int insertDataQuality(DataQuality entity) { return mapper.insertDataQuality(entity); }

    @Override
    public int updateDataQuality(DataQuality entity) { return mapper.updateDataQuality(entity); }

    @Override
    public int deleteDataQualityById(Long id) { return mapper.deleteDataQualityById(id); }

    @Override
    public int deleteDataQualityByIds(Long[] ids) { return mapper.deleteDataQualityByIds(ids); }
}
