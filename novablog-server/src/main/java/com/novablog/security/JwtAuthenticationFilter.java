package com.novablog.security;

import com.novablog.common.UserContext;
import com.novablog.util.RedisUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsServiceImpl userDetailsService;
    private final RedisUtil redisUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String token = extractToken(request);
        if (token == null) {
            chain.doFilter(request, response);
            return;
        }

        // 校验 Token 黑名单
        String jti = jwtTokenProvider.getJti(token);
        if (redisUtil.hasKey("token:blacklist:" + jti)) {
            chain.doFilter(request, response);
            return;
        }

        // 校验 Token 有效性
        if (!jwtTokenProvider.validateToken(token)) {
            chain.doFilter(request, response);
            return;
        }

        // 校验 Token 类型（必须是 ACCESS）
        if (!jwtTokenProvider.isAccessToken(token)) {
            chain.doFilter(request, response);
            return;
        }

        // 加载用户信息
        Long userId = jwtTokenProvider.getUserId(token);
        SecurityUserDetails userDetails =
            (SecurityUserDetails) userDetailsService.loadUserByUserId(userId);

        // 校验用户状态
        if (!userDetails.isEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        // 存入 SecurityContext
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 设置 UserContext（兼容现有 Controller）
        UserContext.set(userDetails.getUserId(),
                        userDetails.getUsername(),
                        userDetails.getRoles());

        chain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
