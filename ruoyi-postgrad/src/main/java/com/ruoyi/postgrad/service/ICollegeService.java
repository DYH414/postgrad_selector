package com.ruoyi.postgrad.service;

import java.util.List;
import com.ruoyi.postgrad.domain.College;

public interface ICollegeService {
    List<College> selectCollegeList(College entity);
    College selectCollegeById(Long id);
    int insertCollege(College entity);
    int updateCollege(College entity);
    int deleteCollegeById(Long id);
    int deleteCollegeByIds(Long[] ids);
}
