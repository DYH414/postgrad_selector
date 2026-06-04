package com.ruoyi.web.controller.postgrad;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.model.AppLoginUser;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.framework.web.service.AppTokenService;
import com.ruoyi.postgrad.domain.AppUser;
import com.ruoyi.postgrad.domain.UserProfile;
import com.ruoyi.postgrad.service.IAppUserService;
import com.ruoyi.postgrad.service.IUserProfileService;

@Anonymous
@RestController
@RequestMapping("/app/auth")
public class AppAuthController
{
    @Autowired
    private IAppUserService appUserService;

    @Autowired
    private IUserProfileService profileService;

    @Autowired
    private AppTokenService appTokenService;

    @Autowired
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @PostMapping("/register")
    public AjaxResult register(@RequestBody Map<String, String> body)
    {
        String phone = body.get("phone");
        String email = body.get("email");
        String password = body.get("password");

        if ((phone == null || phone.isBlank()) && (email == null || email.isBlank()))
        {
            return AjaxResult.error("手机号或邮箱至少填写一项");
        }
        if (password == null || password.length() < 6)
        {
            return AjaxResult.error("密码至少6位");
        }

        AppUser user = new AppUser();
        if (phone != null && !phone.isBlank())
        {
            String phoneHash = sha256(phone.trim());
            if (appUserService.selectCountByPhoneHash(phoneHash) > 0)
            {
                return AjaxResult.error("该手机号已注册");
            }
            user.setPhoneHash(phoneHash);
        }
        if (email != null && !email.isBlank())
        {
            String emailHash = sha256(email.trim().toLowerCase());
            if (appUserService.selectCountByEmailHash(emailHash) > 0)
            {
                return AjaxResult.error("该邮箱已注册");
            }
            user.setEmailHash(emailHash);
        }

        user.setPasswordHash(bCryptPasswordEncoder.encode(password));
        appUserService.insertAppUser(user);
        return AjaxResult.success("注册成功");
    }

    @PostMapping("/login")
    public AjaxResult login(@RequestBody Map<String, String> body)
    {
        String account = body.get("account");
        String password = body.get("password");

        if (account == null || account.isBlank() || password == null || password.isBlank())
        {
            return AjaxResult.error("账号和密码不能为空");
        }

        String phoneHash = sha256(account.trim());
        String emailHash = sha256(account.trim().toLowerCase());
        AppUser user = appUserService.selectAppUserByPhoneHash(phoneHash);
        if (user == null)
        {
            user = appUserService.selectAppUserByEmailHash(emailHash);
        }

        if (user == null || !bCryptPasswordEncoder.matches(password, user.getPasswordHash()))
        {
            return AjaxResult.error("账号或密码错误");
        }

        AppLoginUser loginUser = new AppLoginUser(user.getId());
        String token = appTokenService.createToken(loginUser);

        AjaxResult result = AjaxResult.success("登录成功");
        result.put("token", token);
        result.put("userId", user.getId());
        return result;
    }

    @PostMapping("/logout")
    public AjaxResult logout()
    {
        AppLoginUser loginUser = getCurrentAppUser();
        if (loginUser != null)
        {
            appTokenService.delLoginUser(loginUser.getToken());
        }
        return AjaxResult.success("已退出");
    }

    @GetMapping("/me")
    public AjaxResult me()
    {
        AppLoginUser loginUser = getCurrentAppUser();
        if (loginUser == null) return AjaxResult.error("未登录");

        Long userId = loginUser.getUserId();
        AppUser user = appUserService.selectAppUserById(userId);
        UserProfile profile = profileService.selectUserProfileByUserId(userId);

        AjaxResult result = AjaxResult.success();
        result.put("userId", userId);
        result.put("role", user != null ? user.getRole() : "user");
        result.put("createdAt", user != null ? user.getCreatedAt() : null);
        if (profile != null)
        {
            Map<String, Object> profileMap = new LinkedHashMap<>();
            profileMap.put("userId", profile.getUserId());
            profileMap.put("estimatedScore", profile.getEstimatedScore());
            profileMap.put("targetRegions", profile.getTargetRegions());
            profileMap.put("acceptPartTime", profile.getAcceptPartTime());
            profileMap.put("acceptTransfer", profile.getAcceptTransfer());
            profileMap.put("acceptAcademic", profile.getAcceptAcademic());
            profileMap.put("acceptJoint", profile.getAcceptJoint());
            profileMap.put("riskPreference", profile.getRiskPreference());
            profileMap.put("schoolTierPreference", profile.getSchoolTierPreference());
            profileMap.put("regionStrategy", profile.getRegionStrategy());
            profileMap.put("undergradTier", profile.getUndergradTier());
            profileMap.put("undergraduateMajor", profile.getUndergraduateMajor());
            profileMap.put("isCrossMajor", profile.getIsCrossMajor());
            profileMap.put("mathLevel", profile.getMathLevel());
            profileMap.put("englishLevel", profile.getEnglishLevel());
            profileMap.put("csLevel", profile.getCsLevel());
            profileMap.put("dailyStudyHours", profile.getDailyStudyHours());
            profileMap.put("reviewProgress", profile.getReviewProgress());
            result.put("profile", profileMap);
        }
        return result;
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

    private static String sha256(String input)
    {
        try
        {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new RuntimeException(e);
        }
    }
}
