package com.ruoyi.web.controller.postgrad;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.model.AppLoginUser;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.postgrad.service.IUserFavoriteProgramService;

@RestController
@RequestMapping("/app/favorites")
public class AppFavoriteController
{
    @Autowired
    private IUserFavoriteProgramService favoriteService;

    @GetMapping
    public AjaxResult list()
    {
        AppLoginUser loginUser = getCurrentAppUser();
        if (loginUser == null) return AjaxResult.error("未登录");

        return AjaxResult.success(normalizeFavorites(favoriteService.selectFavoriteListByUserId(loginUser.getUserId())));
    }

    @PostMapping("/{programId}")
    public AjaxResult add(@PathVariable Long programId, @RequestBody(required = false) Map<String, String> body)
    {
        AppLoginUser loginUser = getCurrentAppUser();
        if (loginUser == null) return AjaxResult.error("未登录");

        String note = body != null ? body.get("note") : null;
        int rows = favoriteService.addFavorite(loginUser.getUserId(), programId, note);
        return rows > 0 ? AjaxResult.success("已加入收藏") : AjaxResult.success("已收藏，无需重复操作");
    }

    @DeleteMapping("/{programId}")
    public AjaxResult remove(@PathVariable Long programId)
    {
        AppLoginUser loginUser = getCurrentAppUser();
        if (loginUser == null) return AjaxResult.error("未登录");

        favoriteService.removeFavorite(loginUser.getUserId(), programId);
        return AjaxResult.success("已取消收藏");
    }

    private AppLoginUser getCurrentAppUser()
    {
        try
        {
            Authentication auth = SecurityUtils.getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof AppLoginUser)
            {
                return (AppLoginUser) auth.getPrincipal();
            }
            return null;
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private List<Map<String, Object>> normalizeFavorites(List<Map<String, Object>> favorites)
    {
        List<Map<String, Object>> list = new ArrayList<>();
        if (favorites == null) return list;
        for (Map<String, Object> row : favorites)
        {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.get("id"));
            item.put("programId", row.get("program_id"));
            item.put("schoolName", row.get("school_name"));
            item.put("tier", row.get("tier"));
            item.put("collegeName", row.get("college_name"));
            item.put("programName", row.get("program_name"));
            item.put("programCode", row.get("program_code"));
            item.put("degreeType", row.get("degree_type"));
            item.put("studyMode", row.get("study_mode"));
            item.put("createdAt", row.get("created_at"));
            item.put("note", row.get("note"));
            list.add(item);
        }
        return list;
    }
}
