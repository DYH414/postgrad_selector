package com.ruoyi.postgrad.service;

import java.util.List;
import com.ruoyi.postgrad.domain.AdmissionScore;

public interface IAdmissionScoreService {
    List<AdmissionScore> selectAdmissionScoreList(AdmissionScore entity);
    AdmissionScore selectAdmissionScoreById(Long id);
    int insertAdmissionScore(AdmissionScore entity);
    int updateAdmissionScore(AdmissionScore entity);
    int deleteAdmissionScoreById(Long id);
    int deleteAdmissionScoreByIds(Long[] ids);
}
