package com.ruoyi.postgrad.service;

import java.util.List;
import com.ruoyi.postgrad.domain.Subject;

public interface ISubjectService {
    List<Subject> selectSubjectList(Subject entity);
    Subject selectSubjectById(Long id);
    int insertSubject(Subject entity);
    int updateSubject(Subject entity);
    int deleteSubjectById(Long id);
    int deleteSubjectByIds(Long[] ids);
}
