package com.ruoyi.postgrad.service;

import java.util.List;
import com.ruoyi.postgrad.domain.Program;

public interface IProgramService {
    List<Program> selectProgramList(Program entity);
    Program selectProgramById(Long id);
    int insertProgram(Program entity);
    int updateProgram(Program entity);
    int deleteProgramById(Long id);
    int deleteProgramByIds(Long[] ids);
}
