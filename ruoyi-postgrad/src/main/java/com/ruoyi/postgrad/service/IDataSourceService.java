package com.ruoyi.postgrad.service;

import java.util.List;
import com.ruoyi.postgrad.domain.DataSource;

public interface IDataSourceService {
    List<DataSource> selectDataSourceList(DataSource entity);
    DataSource selectDataSourceById(Long id);
    int insertDataSource(DataSource entity);
    int updateDataSource(DataSource entity);
    int deleteDataSourceById(Long id);
    int deleteDataSourceByIds(Long[] ids);
}
