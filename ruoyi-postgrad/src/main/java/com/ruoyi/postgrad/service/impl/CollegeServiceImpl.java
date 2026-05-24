package com.ruoyi.postgrad.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.postgrad.domain.College;
import com.ruoyi.postgrad.mapper.CollegeMapper;
import com.ruoyi.postgrad.service.ICollegeService;

@Service
public class CollegeServiceImpl implements ICollegeService {
    @Autowired
    private CollegeMapper mapper;

    @Override
    public List<College> selectCollegeList(College entity) { return mapper.selectCollegeList(entity); }

    @Override
    public College selectCollegeById(Long id) { return mapper.selectCollegeById(id); }

    @Override
    public int insertCollege(College entity) { return mapper.insertCollege(entity); }

    @Override
    public int updateCollege(College entity) { return mapper.updateCollege(entity); }

    @Override
    public int deleteCollegeById(Long id) { return mapper.deleteCollegeById(id); }

    @Override
    public int deleteCollegeByIds(Long[] ids) { return mapper.deleteCollegeByIds(ids); }
}
