package com.ruoyi.postgrad.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.postgrad.domain.Subject;
import com.ruoyi.postgrad.mapper.SubjectMapper;
import com.ruoyi.postgrad.service.ISubjectService;

@Service
public class SubjectServiceImpl implements ISubjectService {
    @Autowired
    private SubjectMapper mapper;

    @Override
    public List<Subject> selectSubjectList(Subject entity) { return mapper.selectSubjectList(entity); }

    @Override
    public Subject selectSubjectById(Long id) { return mapper.selectSubjectById(id); }

    @Override
    public int insertSubject(Subject entity) { return mapper.insertSubject(entity); }

    @Override
    public int updateSubject(Subject entity) { return mapper.updateSubject(entity); }

    @Override
    public int deleteSubjectById(Long id) { return mapper.deleteSubjectById(id); }

    @Override
    public int deleteSubjectByIds(Long[] ids) { return mapper.deleteSubjectByIds(ids); }
}
