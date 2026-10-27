package com.novablog.security;

import com.novablog.common.UserContext;
import com.novablog.service.SessionService;
import com.novablog.service.TokenBlacklistService;
import com.novablog.util.RequestUtils;
import java.util.List;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsServiceImpl userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;
    private final SessionService sessionService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {
        try {
            authenticate(request);
            chain.doFilter(request, response);
        } finally {
            // ThreadLocal 必须显式清理：Tomcat 复用线程时，残留的用户信息会被
            // 下一个请求读到——尤其是放行的公开接口，会拿到上一位访问者的 userId
            UserContext.clear();
        }
    }

    /**
     * 校验令牌并写入认证上下文。
     * 任一步不通过都只表现为「未认证」，由后续的授权规则决定该请求是否放行。
     */
    private void authenticate(HttpServletRequest request) {
        String token = extractToken(request);
        if (token == null) {
            return;
        }

        // 先验签：被篡改的令牌在解析 claim 时就会抛异常，不能拿它去取 jti
        if (!jwtTokenProvider.validateToken(token)) {
            return;
        }

        // 必须是 AccessToken，RefreshToken 不能用来访问业务接口
        if (!jwtTokenProvider.isAccessToken(token)) {
            return;
        }

        String sid = jwtTokenProvider.getSid(token);

        // 会话级失效：登出或判定令牌泄露后，该会话的全部令牌立即作废。
        // 标记原因是为了让前端知道「刷新也救不回来」，不必再白跑一次刷新
        if (tokenBlacklistService.isSessionRevoked(sid)) {
            request.setAttribute(AuthenticationEntryPointImpl.ATTR_REJECT_REASON,
                AuthenticationEntryPointImpl.REASON_SESSION_REVOKED);
            return;
        }

        // 单令牌失效：登出的 AT、被轮换掉的旧 RT，以及不含 sid 的存量令牌
        if (tokenBlacklistService.isBlacklisted(jwtTokenProvider.getJti(token))) {
            return;
        }

        // 加载用户信息。用户已被删除时按未认证处理，
        // 异常若冒泡会绕开安全链的异常翻译，最终变成 500
        Long userId = jwtTokenProvider.getUserId(token);
        SecurityUserDetails userDetails;
        try {
            userDetails = (SecurityUserDetails) userDetailsService.loadUserByUserId(userId);
        } catch (UsernameNotFoundException e) {
            return;
        }

        // 校验用户状态
        if (!userDetails.isEnabled()) {
            request.setAttribute(AuthenticationEntryPointImpl.ATTR_REJECT_REASON,
                AuthenticationEntryPointImpl.REASON_USER_DISABLED);
            return;
        }

        // 存入 SecurityContext
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 设置 UserContext（兼容现有 Controller，只传入角色不含权限）
        List<String> roles = userDetails.getRoles().stream()
            .filter(a -> a.startsWith("ROLE_"))
            .toList();
        UserContext.set(userDetails.getUserId(),
                        userDetails.getUsername(),
                        roles,
                        sid);

        // 活跃度更新：内部有 60 秒节流，且吞掉所有异常，
        // 会话中心故障不能让正常请求被拖垮
        sessionService.touch(userId, sid,
            RequestUtils.getClientIp(request), RequestUtils.getUserAgent(request));
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
