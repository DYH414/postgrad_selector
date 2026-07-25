package com.ruoyi.framework.security.filter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 内部评测端点（/internal/**）的共享密钥认证。
 * Python 评测 Worker 用 X-Evaluation-Token 头调用；常量时间比较防时序侧信道。
 * fail-closed：token 未配置或不匹配则不设置认证，端点因缺少 ROLE_INTERNAL_EVAL 被拒。
 */
public class InternalTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Evaluation-Token";
    private static final String PATH_PREFIX = "/internal/";
    private final String expectedToken;

    public InternalTokenAuthenticationFilter(String expectedToken) {
        this.expectedToken = expectedToken;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path == null || !path.startsWith(PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        if (expectedToken != null && !expectedToken.isBlank()
            && provided != null && !provided.isBlank()
            && constantTimeEquals(expectedToken, provided)
            && SecurityContextHolder.getContext().getAuthentication() == null) {
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "internal-evaluation", null, List.of(new SimpleGrantedAuthority("ROLE_INTERNAL_EVAL")));
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        chain.doFilter(request, response);
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
