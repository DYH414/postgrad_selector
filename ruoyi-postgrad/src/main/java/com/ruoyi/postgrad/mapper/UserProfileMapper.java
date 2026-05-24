package com.ruoyi.postgrad.mapper;

import com.ruoyi.postgrad.domain.UserProfile;

public interface UserProfileMapper
{
    UserProfile selectUserProfileByUserId(Long userId);
    int insertUserProfile(UserProfile profile);
    int updateUserProfile(UserProfile profile);
    int deleteUserProfileByUserId(Long userId);
}
