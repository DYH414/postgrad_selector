package com.ruoyi.postgrad.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.exception.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.postgrad.domain.UserFavoriteProgram;
import com.ruoyi.postgrad.mapper.UserFavoriteProgramMapper;
import com.ruoyi.postgrad.service.IUserFavoriteProgramService;

@Service
public class UserFavoriteProgramServiceImpl implements IUserFavoriteProgramService
{
    @Autowired
    private UserFavoriteProgramMapper favoriteMapper;

    @Override
    public List<Map<String, Object>> selectFavoriteListByUserId(Long userId)
    {
        return new ArrayList<>(favoriteMapper.selectFavoriteListByUserId(userId));
    }

    @Override
    public boolean isFavorited(Long userId, Long programId)
    {
        return favoriteMapper.selectCountByUserIdAndProgramId(userId, programId) > 0;
    }

    @Override
    public int addFavorite(Long userId, Long programId, String note)
    {
        if (isFavorited(userId, programId))
        {
            return 0;
        }
        UserFavoriteProgram fav = new UserFavoriteProgram();
        fav.setUserId(userId);
        fav.setProgramId(programId);
        fav.setNote(note);
        int rows = favoriteMapper.insertUserFavoriteProgram(fav);
        if (rows == 0 && !isFavorited(userId, programId))
        {
            throw new ServiceException("专业不存在或暂无可用数据", HttpStatus.NOT_FOUND);
        }
        return rows;
    }

    @Override
    public int removeFavorite(Long userId, Long programId)
    {
        return favoriteMapper.deleteUserFavoriteProgram(userId, programId);
    }
}
