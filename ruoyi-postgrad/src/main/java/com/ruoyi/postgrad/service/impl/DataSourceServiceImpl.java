package com.ruoyi.postgrad.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.postgrad.domain.DataSource;
import com.ruoyi.postgrad.mapper.DataSourceMapper;
import com.ruoyi.postgrad.service.IDataSourceService;

@Service
public class DataSourceServiceImpl implements IDataSourceService {
    @Autowired
    private DataSourceMapper mapper;

    @Override
    public List<DataSource> selectDataSourceList(DataSource entity) { return mapper.selectDataSourceList(entity); }

    @Override
    public DataSource selectDataSourceById(Long id) { return mapper.selectDataSourceById(id); }

    @Override
    public int insertDataSource(DataSource entity) { return mapper.insertDataSource(entity); }

    @Override
    public int updateDataSource(DataSource entity) { return mapper.updateDataSource(entity); }

    @Override
    public int deleteDataSourceById(Long id) { return mapper.deleteDataSourceById(id); }

    @Override
    public int deleteDataSourceByIds(Long[] ids) { return mapper.deleteDataSourceByIds(ids); }
}
