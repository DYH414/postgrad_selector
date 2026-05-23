package com.ruoyi.web.controller.postgrad;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.model.AppLoginUser;
import com.ruoyi.common.utils.SecurityUtils;

@RestController
@RequestMapping("/app/favorites")
public class AppFavoriteController
{
    @Autowired
    private JdbcTemplate jdbc;

    @GetMapping
    public AjaxResult list()
    {
        AppLoginUser loginUser = getCurrentAppUser();
        if (loginUser == null) return AjaxResult.error("未登录");

        List<Map<String, Object>> favorites = jdbc.queryForList(
            "SELECT ufp.id, ufp.program_id, ufp.note, ufp.created_at, " +
            "p.program_code, p.program_name, p.study_mode, p.degree_type, " +
            "c.name college_name, s.name school_name, s.province, s.tier, s.is_985, s.is_211, s.is_double_first " +
            "FROM user_favorite_program ufp " +
            "JOIN program p ON ufp.program_id = p.id " +
            "JOIN college c ON p.college_id = c.id " +
            "JOIN school s ON c.school_id = s.id " +
            "WHERE ufp.user_id = ? ORDER BY ufp.created_at DESC",
            loginUser.getUserId());

        return AjaxResult.success(favorites);
    }

    @PostMapping("/{programId}")
    public AjaxResult add(@PathVariable Long programId)
    {
        AppLoginUser loginUser = getCurrentAppUser();
        if (loginUser == null) return AjaxResult.error("未登录");

        // check duplicate
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM user_favorite_program WHERE user_id = ? AND program_id = ?",
            Integer.class, loginUser.getUserId(), programId);
        if (count != null && count > 0)
        {
            return AjaxResult.success("已收藏");
        }

        jdbc.update(
            "INSERT INTO user_favorite_program (user_id, program_id, created_at, updated_at) VALUES (?, ?, NOW(), NOW())",
            loginUser.getUserId(), programId);

        return AjaxResult.success("收藏成功");
    }

    @DeleteMapping("/{programId}")
    public AjaxResult remove(@PathVariable Long programId)
    {
        AppLoginUser loginUser = getCurrentAppUser();
        if (loginUser == null) return AjaxResult.error("未登录");

        jdbc.update(
            "DELETE FROM user_favorite_program WHERE user_id = ? AND program_id = ?",
            loginUser.getUserId(), programId);

        return AjaxResult.success("已取消收藏");
    }

    private AppLoginUser getCurrentAppUser()
    {
        try
        {
            Object principal = SecurityUtils.getAuthentication().getPrincipal();
            if (principal instanceof AppLoginUser) return (AppLoginUser) principal;
            return null;
        }
        catch (Exception e)
        {
            return null;
        }
    }
}
