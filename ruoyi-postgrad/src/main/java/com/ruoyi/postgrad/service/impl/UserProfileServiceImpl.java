package com.ruoyi.postgrad.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.postgrad.domain.UserProfile;
import com.ruoyi.postgrad.mapper.UserProfileMapper;
import com.ruoyi.postgrad.service.IUserProfileService;

@Service
public class UserProfileServiceImpl implements IUserProfileService
{
    @Autowired
    private UserProfileMapper userProfileMapper;

    @Override
    public UserProfile selectUserProfileByUserId(Long userId)
    {
        return userProfileMapper.selectUserProfileByUserId(userId);
    }

    @Override
    public int insertUserProfile(UserProfile profile)
    {
        return userProfileMapper.insertUserProfile(profile);
    }

    @Override
    public int updateUserProfile(UserProfile profile)
    {
        return userProfileMapper.updateUserProfile(profile);
    }

    @Override
    public int deleteUserProfileByUserId(Long userId)
    {
        return userProfileMapper.deleteUserProfileByUserId(userId);
    }

    @Override
    public int saveOrUpdateUserProfile(Long userId, UserProfile profile)
    {
        UserProfile existing = userProfileMapper.selectUserProfileByUserId(userId);
        profile.setUserId(userId);
        if (existing == null)
        {
            return userProfileMapper.insertUserProfile(profile);
        }
        return userProfileMapper.updateUserProfile(profile);
    }
}
