package com.ruoyi.postgrad.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import com.ruoyi.postgrad.domain.AdmissionPlan;

@Mapper
public interface AdmissionPlanMapper {
    List<AdmissionPlan> selectAdmissionPlanList(AdmissionPlan entity);
    AdmissionPlan selectAdmissionPlanById(Long id);
    int insertAdmissionPlan(AdmissionPlan entity);
    int updateAdmissionPlan(AdmissionPlan entity);
    int deleteAdmissionPlanById(Long id);
    int deleteAdmissionPlanByIds(Long[] ids);
}
