package com.ruoyi.postgrad.service;

import com.ruoyi.postgrad.domain.UserProfile;

public interface IUserProfileService
{
    UserProfile selectUserProfileByUserId(Long userId);
    int insertUserProfile(UserProfile profile);
    int updateUserProfile(UserProfile profile);
    int deleteUserProfileByUserId(Long userId);
    int saveOrUpdateUserProfile(Long userId, UserProfile profile);
}
