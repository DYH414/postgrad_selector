package com.ruoyi.postgrad.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.postgrad.domain.AppUser;
import com.ruoyi.postgrad.mapper.AppUserMapper;
import com.ruoyi.postgrad.service.IAppUserService;

@Service
public class AppUserServiceImpl implements IAppUserService
{
    @Autowired
    private AppUserMapper appUserMapper;

    @Override
    public AppUser selectAppUserById(Long id)
    {
        return appUserMapper.selectAppUserById(id);
    }

    @Override
    public AppUser selectAppUserByPhoneHash(String phoneHash)
    {
        return appUserMapper.selectAppUserByPhoneHash(phoneHash);
    }

    @Override
    public AppUser selectAppUserByEmailHash(String emailHash)
    {
        return appUserMapper.selectAppUserByEmailHash(emailHash);
    }

    @Override
    public int selectCountByPhoneHash(String phoneHash)
    {
        return appUserMapper.selectCountByPhoneHash(phoneHash);
    }

    @Override
    public int selectCountByEmailHash(String emailHash)
    {
        return appUserMapper.selectCountByEmailHash(emailHash);
    }

    @Override
    public int insertAppUser(AppUser user)
    {
        return appUserMapper.insertAppUser(user);
    }
}
