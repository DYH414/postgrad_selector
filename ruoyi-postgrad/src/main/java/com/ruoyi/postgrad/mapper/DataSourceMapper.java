package com.ruoyi.postgrad.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import com.ruoyi.postgrad.domain.DataSource;

@Mapper
public interface DataSourceMapper {
    List<DataSource> selectDataSourceList(DataSource entity);
    DataSource selectDataSourceById(Long id);
    int insertDataSource(DataSource entity);
    int updateDataSource(DataSource entity);
    int deleteDataSourceById(Long id);
    int deleteDataSourceByIds(Long[] ids);
}
