package com.ruoyi.web.controller.postgrad;

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

        return AjaxResult.success(favoriteService.selectFavoriteListByUserId(loginUser.getUserId()));
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
        catch (Exception e) { return null; }
    }
}
