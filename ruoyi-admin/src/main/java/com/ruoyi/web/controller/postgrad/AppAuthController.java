package com.ruoyi.web.controller.postgrad;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.model.AppLoginUser;
import com.ruoyi.framework.web.service.AppTokenService;
import org.springframework.security.core.Authentication;
import com.ruoyi.common.utils.SecurityUtils;

@Anonymous
@RestController
@RequestMapping("/app/auth")
public class AppAuthController
{
    @Autowired
    private JdbcTemplate jdbc;

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

        String phoneHash = null;
        String emailHash = null;

        if (phone != null && !phone.isBlank())
        {
            phoneHash = sha256(phone.trim());
            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM app_user WHERE phone_hash = ? AND deleted_at IS NULL", Integer.class, phoneHash);
            if (count != null && count > 0)
            {
                return AjaxResult.error("该手机号已注册");
            }
        }

        if (email != null && !email.isBlank())
        {
            emailHash = sha256(email.trim().toLowerCase());
            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM app_user WHERE email_hash = ? AND deleted_at IS NULL", Integer.class, emailHash);
            if (count != null && count > 0)
            {
                return AjaxResult.error("该邮箱已注册");
            }
        }

        String passwordHash = bCryptPasswordEncoder.encode(password);
        jdbc.update(
            "INSERT INTO app_user (phone_hash, email_hash, password_hash, created_at, updated_at) VALUES (?, ?, ?, NOW(), NOW())",
            phoneHash, emailHash, passwordHash);

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

        Map<String, Object> user = null;
        try
        {
            user = jdbc.queryForMap(
                "SELECT id, password_hash FROM app_user WHERE (phone_hash = ? OR email_hash = ?) AND deleted_at IS NULL LIMIT 1",
                phoneHash, emailHash);
        }
        catch (Exception e)
        {
            return AjaxResult.error("账号或密码错误");
        }

        if (user == null || !bCryptPasswordEncoder.matches(password, (String) user.get("password_hash")))
        {
            return AjaxResult.error("账号或密码错误");
        }

        Long userId = ((Number) user.get("id")).longValue();
        AppLoginUser loginUser = new AppLoginUser(userId);
        String token = appTokenService.createToken(loginUser);

        AjaxResult result = AjaxResult.success("登录成功");
        result.put("token", token);
        result.put("userId", userId);
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
        if (loginUser == null)
        {
            return AjaxResult.error("未登录");
        }
        Long userId = loginUser.getUserId();
        Map<String, Object> profile = null;
        try
        {
            profile = jdbc.queryForMap(
                "SELECT up.* FROM user_profile up WHERE up.user_id = ?", userId);
        }
        catch (Exception ignored) {}

        Map<String, Object> user = jdbc.queryForMap(
            "SELECT id, role, created_at FROM app_user WHERE id = ? AND deleted_at IS NULL", userId);

        AjaxResult result = AjaxResult.success();
        result.put("userId", userId);
        result.put("role", user.get("role"));
        result.put("createdAt", user.get("created_at"));
        result.put("profile", profile);
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
        catch (Exception e)
        {
            return null;
        }
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
