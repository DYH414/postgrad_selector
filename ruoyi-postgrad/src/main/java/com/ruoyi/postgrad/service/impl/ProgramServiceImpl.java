package com.ruoyi.postgrad.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.postgrad.domain.Program;
import com.ruoyi.postgrad.mapper.ProgramMapper;
import com.ruoyi.postgrad.service.IProgramService;

@Service
public class ProgramServiceImpl implements IProgramService {
    @Autowired
    private ProgramMapper mapper;

    @Override
    public List<Program> selectProgramList(Program entity) { return mapper.selectProgramList(entity); }

    @Override
    public Program selectProgramById(Long id) { return mapper.selectProgramById(id); }

    @Override
    public int insertProgram(Program entity) { return mapper.insertProgram(entity); }

    @Override
    public int updateProgram(Program entity) { return mapper.updateProgram(entity); }

    @Override
    public int deleteProgramById(Long id) { return mapper.deleteProgramById(id); }

    @Override
    public int deleteProgramByIds(Long[] ids) { return mapper.deleteProgramByIds(ids); }
}
