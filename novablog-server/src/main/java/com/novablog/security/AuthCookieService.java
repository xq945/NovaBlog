package com.novablog.security;

import com.novablog.config.AuthCookieProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * RefreshToken 的 Cookie 读写
 *
 * <p>用 {@link ResponseCookie} 而不是 Servlet 的 {@link Cookie}：后者无法设置 SameSite，
 * 而 SameSite=Lax 是这套方案挡住 CSRF 的第一道防线。
 *
 * <p>清除时 Path 与 Domain 必须与写入时完全一致，否则浏览器不会删除 Cookie；
 * 同一个 {@link #buildCookie(String, long)} 保证了两处属性同源。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthCookieService {

    private final AuthCookieProperties properties;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 写入 RefreshToken Cookie
     *
     * <p>Max-Age 取令牌自身剩余有效期而不是固定 7 天：刷新宽限分支复用旧 RT 时，
     * 剩余时间可能只有几秒，写死会让 Cookie 比令牌活得久，留下一段「浏览器每次都带、
     * 服务端每次都拒」的无效窗口。
     */
    public void writeRefreshToken(HttpServletResponse response, String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return;
        }
        addSetCookie(response, buildCookie(refreshToken, resolveMaxAgeSeconds(refreshToken)));
    }

    /**
     * 把服务层返回结果里的 RefreshToken 移到 Cookie 中
     *
     * <p>登录、注册、刷新三个入口共用：从响应体剔除 RT（前端 JS 不该接触它），
     * 写 Set-Cookie，并标记 no-store 避免中间层缓存带令牌的响应
     *
     * @param data 服务层返回的响应数据，其中 refreshToken 字段会被移除
     */
    public void moveRefreshTokenToCookie(Map<String, Object> data, HttpServletResponse response) {
        Object refreshToken = data.remove("refreshToken");
        if (refreshToken instanceof String token) {
            writeRefreshToken(response, token);
        }
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    /**
     * 清除 RefreshToken Cookie
     */
    public void clearRefreshToken(HttpServletResponse response) {
        addSetCookie(response, buildCookie("", 0));
    }

    /**
     * 解析请求携带的 RefreshToken
     *
     * <p>Cookie 优先；过渡期允许回退到 Authorization: Bearer，兼容尚未升级的调用方。
     * 回退只接受 Bearer 前缀，避免把误放在此头里的 AccessToken 当成 RT
     */
    public String resolveRefreshToken(HttpServletRequest request) {
        String fromCookie = readRefreshToken(request);
        if (StringUtils.hasText(fromCookie)) {
            return fromCookie;
        }
        if (!properties.isAllowHeaderFallback()) {
            return null;
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            return StringUtils.hasText(token) ? token : null;
        }
        return null;
    }

    /**
     * 只从 Cookie 读取，不做 header 回退。登出用它判断「本次撤销是否由共享 Cookie 的持有者发起」
     */
    public String readRefreshToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        String name = properties.getRefreshTokenName();
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * 计算 Cookie 存活秒数，上限为 RT 完整有效期
     */
    private long resolveMaxAgeSeconds(String refreshToken) {
        try {
            long remaining = jwtTokenProvider.getRemainingMillis(refreshToken);
            if (remaining <= 0) {
                return 0;
            }
            long cap = jwtTokenProvider.getRefreshExpirationMillis();
            return Math.min(remaining, cap) / 1000;
        } catch (Exception e) {
            // 解析失败说明签发链路本身有问题；此处不抛异常打断响应，
            // 退回完整有效期，令牌仍会被后续校验拦下
            log.warn("计算 RefreshToken Cookie 有效期失败，使用完整有效期", e);
            return jwtTokenProvider.getRefreshExpirationMillis() / 1000;
        }
    }

    private ResponseCookie buildCookie(String value, long maxAgeSeconds) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie
            .from(properties.getRefreshTokenName(), value)
            .path(properties.getPath())
            .maxAge(maxAgeSeconds)
            .httpOnly(true)
            .secure(properties.isSecure())
            .sameSite(properties.getSameSite());
        // Domain 为空时不能设置，否则会写出 "Domain=" 导致浏览器丢弃 Cookie
        if (StringUtils.hasText(properties.getDomain())) {
            builder.domain(properties.getDomain());
        }
        return builder.build();
    }

    private void addSetCookie(HttpServletResponse response, ResponseCookie cookie) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
