package com.ruoyi.postgrad.service;

import java.util.List;
import com.ruoyi.postgrad.domain.AdmissionPlan;

public interface IAdmissionPlanService {
    List<AdmissionPlan> selectAdmissionPlanList(AdmissionPlan entity);
    AdmissionPlan selectAdmissionPlanById(Long id);
    int insertAdmissionPlan(AdmissionPlan entity);
    int updateAdmissionPlan(AdmissionPlan entity);
    int deleteAdmissionPlanById(Long id);
    int deleteAdmissionPlanByIds(Long[] ids);
}
