package com.ruoyi.postgrad.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.postgrad.domain.AdmissionScore;
import com.ruoyi.postgrad.mapper.AdmissionScoreMapper;
import com.ruoyi.postgrad.service.IAdmissionScoreService;

@Service
public class AdmissionScoreServiceImpl implements IAdmissionScoreService {
    @Autowired
    private AdmissionScoreMapper mapper;

    @Override
    public List<AdmissionScore> selectAdmissionScoreList(AdmissionScore entity) { return mapper.selectAdmissionScoreList(entity); }

    @Override
    public AdmissionScore selectAdmissionScoreById(Long id) { return mapper.selectAdmissionScoreById(id); }

    @Override
    public int insertAdmissionScore(AdmissionScore entity) { return mapper.insertAdmissionScore(entity); }

    @Override
    public int updateAdmissionScore(AdmissionScore entity) { return mapper.updateAdmissionScore(entity); }

    @Override
    public int deleteAdmissionScoreById(Long id) { return mapper.deleteAdmissionScoreById(id); }

    @Override
    public int deleteAdmissionScoreByIds(Long[] ids) { return mapper.deleteAdmissionScoreByIds(ids); }
}
