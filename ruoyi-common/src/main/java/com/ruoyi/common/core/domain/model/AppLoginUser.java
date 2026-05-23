package com.ruoyi.common.core.domain.model;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import com.alibaba.fastjson2.annotation.JSONField;

/**
 * App端登录用户身份
 */
public class AppLoginUser implements UserDetails
{
    private static final long serialVersionUID = 1L;

    private Long userId;
    private String token;
    private Long loginTime;
    private Long expireTime;
    private String ipaddr;
    private String browser;
    private String os;

    public AppLoginUser() {}

    public AppLoginUser(Long userId)
    {
        this.userId = userId;
    }

    @JSONField(serialize = false)
    @Override
    public String getPassword() { return null; }

    @Override
    public String getUsername() { return String.valueOf(userId); }

    @JSONField(serialize = false)
    @Override
    public boolean isAccountNonExpired() { return true; }

    @JSONField(serialize = false)
    @Override
    public boolean isAccountNonLocked() { return true; }

    @JSONField(serialize = false)
    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @JSONField(serialize = false)
    @Override
    public boolean isEnabled() { return true; }

    @Override
    @JSONField(serialize = false)
    public Collection<? extends GrantedAuthority> getAuthorities()
    {
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_APP_USER"));
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public Long getLoginTime() { return loginTime; }
    public void setLoginTime(Long loginTime) { this.loginTime = loginTime; }

    public Long getExpireTime() { return expireTime; }
    public void setExpireTime(Long expireTime) { this.expireTime = expireTime; }

    public String getIpaddr() { return ipaddr; }
    public void setIpaddr(String ipaddr) { this.ipaddr = ipaddr; }

    public String getBrowser() { return browser; }
    public void setBrowser(String browser) { this.browser = browser; }

    public String getOs() { return os; }
    public void setOs(String os) { this.os = os; }
}
