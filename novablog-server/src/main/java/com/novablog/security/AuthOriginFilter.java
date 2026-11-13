package com.novablog.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novablog.common.Result;
import com.novablog.config.AuthSecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 认证接口的来源校验（CSRF 防护）
 *
 * <p>RT 改由浏览器自动携带的 Cookie 后，经典跨站表单提交是主要风险面。
 * SameSite=Lax 已经挡住了跨站 POST，但它对「同站不同源」（子域、本机其他端口）无效，
 * 因此再对浏览器可见的 Origin / Referer / Sec-Fetch-Site 做一次白名单校验。
 *
 * <p>没有 Origin 也没有 Referer 的请求（curl、Java 测试脚本、部分 Swagger 场景）直接放行：
 * 非浏览器客户端本来就不存在 CSRF，拦截它们只会让联调和自动化测试无法工作。
 *
 * <p>不引入 CSRF Token：RT 是 HttpOnly，攻击者读不到；双提交 Token 反而要求前端可读一份
 * 凭据，抵消了 HttpOnly 的收益。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthOriginFilter extends OncePerRequestFilter {

    /**
     * 需要校验来源的认证入口。登录/注册也纳入：
     * 防止攻击者把受害者「登录」进自己的账号（login CSRF）
     */
    private static final Set<String> PROTECTED_PATHS = Set.of(
        "/auth/login",
        "/auth/register",
        "/auth/refresh",
        "/auth/logout",
        "/user/login",
        "/user/register"
    );

    private static final String FORWARDED_PREFIX = "/api";

    private final AuthSecurityProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!shouldCheck(request)) {
            chain.doFilter(request, response);
            return;
        }

        // 浏览器明确表示是跨站请求：高置信度信号，直接拒绝
        if ("cross-site".equalsIgnoreCase(request.getHeader("Sec-Fetch-Site"))) {
            reject(request, response, "cross-site");
            return;
        }

        String origin = request.getHeader("Origin");
        if (origin == null) {
            origin = extractOrigin(request.getHeader("Referer"));
        }
        // 两者都没有：非浏览器客户端，放行
        if (origin == null) {
            chain.doFilter(request, response);
            return;
        }

        if (!isAllowedOrigin(origin)) {
            reject(request, response, origin);
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean shouldCheck(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        // 反向代理未剥离 /api 前缀时，后端看到的路径是 /api/auth/refresh
        if (path.startsWith(FORWARDED_PREFIX + "/")) {
            path = path.substring(FORWARDED_PREFIX.length());
        }
        return PROTECTED_PATHS.contains(path);
    }

    private String extractOrigin(String referer) {
        if (referer == null || referer.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(referer);
            if (uri.getScheme() == null || uri.getAuthority() == null) {
                return null;
            }
            return uri.getScheme() + "://" + uri.getAuthority();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean isAllowedOrigin(String origin) {
        // 沙箱 iframe 等场景会发送 Origin: null，不属于任何可信来源
        if (origin == null || "null".equalsIgnoreCase(origin)) {
            return false;
        }
        List<String> allowed = properties.getAllowedOrigins();
        if (allowed == null) {
            return false;
        }
        for (String pattern : allowed) {
            // 单个 * 视为配置错误而不是「放行所有来源」：
            // RT 走 Cookie 后，任意站点都带上凭据调用刷新接口是必须杜绝的
            if (pattern == null || "*".equals(pattern.trim())) {
                continue;
            }
            if (pattern.equals(origin) || wildcardMatches(pattern, origin)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 通配匹配，支持 https://*.cpolar.top 这类子域配置
     */
    private boolean wildcardMatches(String pattern, String origin) {
        if (pattern == null || !pattern.contains("*")) {
            return false;
        }
        StringBuilder regex = new StringBuilder("^");
        for (char c : pattern.toCharArray()) {
            if (c == '*') {
                regex.append(".*");
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        regex.append("$");
        return origin.matches(regex.toString());
    }

    private void reject(HttpServletRequest request, HttpServletResponse response,
                        String origin) throws IOException {
        log.warn("拒绝来源不合法的认证请求: path={}, origin={}", request.getRequestURI(), origin);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
            Result.error(403, "请求来源不合法")));
    }
}
