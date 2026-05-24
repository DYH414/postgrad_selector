package com.ruoyi.postgrad.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import com.ruoyi.postgrad.domain.AdmissionResult;

@Mapper
public interface AdmissionResultMapper {
    List<AdmissionResult> selectAdmissionResultList(AdmissionResult entity);
    AdmissionResult selectAdmissionResultById(Long id);
    int insertAdmissionResult(AdmissionResult entity);
    int updateAdmissionResult(AdmissionResult entity);
    int deleteAdmissionResultById(Long id);
    int deleteAdmissionResultByIds(Long[] ids);
}
