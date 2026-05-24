package com.ruoyi.postgrad.mapper;

import com.ruoyi.postgrad.domain.AppUser;

public interface AppUserMapper
{
    AppUser selectAppUserById(Long id);
    AppUser selectAppUserByPhoneHash(String phoneHash);
    AppUser selectAppUserByEmailHash(String emailHash);
    int selectCountByPhoneHash(String phoneHash);
    int selectCountByEmailHash(String emailHash);
    int insertAppUser(AppUser user);
}
