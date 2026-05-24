package com.ruoyi.postgrad.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import com.ruoyi.postgrad.domain.AdmissionScore;

@Mapper
public interface AdmissionScoreMapper {
    List<AdmissionScore> selectAdmissionScoreList(AdmissionScore entity);
    AdmissionScore selectAdmissionScoreById(Long id);
    int insertAdmissionScore(AdmissionScore entity);
    int updateAdmissionScore(AdmissionScore entity);
    int deleteAdmissionScoreById(Long id);
    int deleteAdmissionScoreByIds(Long[] ids);
}
