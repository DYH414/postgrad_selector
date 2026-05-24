package com.ruoyi.postgrad.service;

import java.util.List;
import java.util.Map;

public interface IUserFavoriteProgramService
{
    List<Map<String, Object>> selectFavoriteListByUserId(Long userId);
    boolean isFavorited(Long userId, Long programId);
    int addFavorite(Long userId, Long programId, String note);
    int removeFavorite(Long userId, Long programId);
}
