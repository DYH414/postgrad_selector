package com.ruoyi.postgrad.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.postgrad.domain.AdmissionPlan;
import com.ruoyi.postgrad.mapper.AdmissionPlanMapper;
import com.ruoyi.postgrad.service.IAdmissionPlanService;

@Service
public class AdmissionPlanServiceImpl implements IAdmissionPlanService {
    @Autowired
    private AdmissionPlanMapper mapper;

    @Override
    public List<AdmissionPlan> selectAdmissionPlanList(AdmissionPlan entity) { return mapper.selectAdmissionPlanList(entity); }

    @Override
    public AdmissionPlan selectAdmissionPlanById(Long id) { return mapper.selectAdmissionPlanById(id); }

    @Override
    public int insertAdmissionPlan(AdmissionPlan entity) { return mapper.insertAdmissionPlan(entity); }

    @Override
    public int updateAdmissionPlan(AdmissionPlan entity) { return mapper.updateAdmissionPlan(entity); }

    @Override
    public int deleteAdmissionPlanById(Long id) { return mapper.deleteAdmissionPlanById(id); }

    @Override
    public int deleteAdmissionPlanByIds(Long[] ids) { return mapper.deleteAdmissionPlanByIds(ids); }
}
