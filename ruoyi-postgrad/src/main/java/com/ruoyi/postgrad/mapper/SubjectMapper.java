package com.ruoyi.postgrad.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import com.ruoyi.postgrad.domain.Subject;

@Mapper
public interface SubjectMapper {
    List<Subject> selectSubjectList(Subject entity);
    Subject selectSubjectById(Long id);
    int insertSubject(Subject entity);
    int updateSubject(Subject entity);
    int deleteSubjectById(Long id);
    int deleteSubjectByIds(Long[] ids);
}
