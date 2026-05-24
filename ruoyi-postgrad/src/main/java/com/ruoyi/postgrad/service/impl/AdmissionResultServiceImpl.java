package com.ruoyi.postgrad.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.postgrad.domain.AdmissionResult;
import com.ruoyi.postgrad.mapper.AdmissionResultMapper;
import com.ruoyi.postgrad.service.IAdmissionResultService;

@Service
public class AdmissionResultServiceImpl implements IAdmissionResultService {
    @Autowired
    private AdmissionResultMapper mapper;

    @Override
    public List<AdmissionResult> selectAdmissionResultList(AdmissionResult entity) { return mapper.selectAdmissionResultList(entity); }

    @Override
    public AdmissionResult selectAdmissionResultById(Long id) { return mapper.selectAdmissionResultById(id); }

    @Override
    public int insertAdmissionResult(AdmissionResult entity) { return mapper.insertAdmissionResult(entity); }

    @Override
    public int updateAdmissionResult(AdmissionResult entity) { return mapper.updateAdmissionResult(entity); }

    @Override
    public int deleteAdmissionResultById(Long id) { return mapper.deleteAdmissionResultById(id); }

    @Override
    public int deleteAdmissionResultByIds(Long[] ids) { return mapper.deleteAdmissionResultByIds(ids); }
}
