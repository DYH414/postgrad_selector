package com.ruoyi.postgrad.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import com.ruoyi.postgrad.domain.College;

@Mapper
public interface CollegeMapper {
    List<College> selectCollegeList(College entity);
    College selectCollegeById(Long id);
    int insertCollege(College entity);
    int updateCollege(College entity);
    int deleteCollegeById(Long id);
    int deleteCollegeByIds(Long[] ids);
}
