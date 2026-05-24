package com.ruoyi.postgrad.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.postgrad.domain.RowMap;
import com.ruoyi.postgrad.domain.UserFavoriteProgram;

public interface UserFavoriteProgramMapper
{
    List<RowMap> selectFavoriteListByUserId(@Param("userId") Long userId);
    int selectCountByUserIdAndProgramId(@Param("userId") Long userId, @Param("programId") Long programId);
    int insertUserFavoriteProgram(UserFavoriteProgram fav);
    int deleteUserFavoriteProgram(@Param("userId") Long userId, @Param("programId") Long programId);
}
