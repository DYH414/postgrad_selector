package com.ruoyi.postgrad.service;

import java.util.List;
import com.ruoyi.postgrad.domain.AdmissionResult;

public interface IAdmissionResultService {
    List<AdmissionResult> selectAdmissionResultList(AdmissionResult entity);
    AdmissionResult selectAdmissionResultById(Long id);
    int insertAdmissionResult(AdmissionResult entity);
    int updateAdmissionResult(AdmissionResult entity);
    int deleteAdmissionResultById(Long id);
    int deleteAdmissionResultByIds(Long[] ids);
}
