package com.ruoyi.postgrad.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import com.ruoyi.postgrad.domain.Program;

@Mapper
public interface ProgramMapper {
    List<Program> selectProgramList(Program entity);
    Program selectProgramById(Long id);
    int insertProgram(Program entity);
    int updateProgram(Program entity);
    int deleteProgramById(Long id);
    int deleteProgramByIds(Long[] ids);
}
