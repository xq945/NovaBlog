package com.novablog.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novablog.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class AuthenticationEntryPointImpl implements AuthenticationEntryPoint {

    /**
     * 认证过滤器写入请求的拒绝原因，用于区分「令牌过期」与「会话已被撤销」
     */
    public static final String ATTR_REJECT_REASON = "AUTH_REJECT_REASON";

    /**
     * 会话已被撤销（登出、被管理员踢下线、判定令牌泄露）
     */
    public static final String REASON_SESSION_REVOKED = "SESSION_REVOKED";

    /**
     * 账号已被禁用
     */
    public static final String REASON_USER_DISABLED = "USER_DISABLED";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException e) throws IOException {
        String reason = (String) request.getAttribute(ATTR_REJECT_REASON);

        // 会话类失效无法靠刷新恢复，前端据此跳过刷新队列直接回登录页；
        // 普通 401 仍交给前端走 RT 刷新
        Result<?> result;
        if (REASON_SESSION_REVOKED.equals(reason)) {
            result = Result.error(4011, "登录状态已失效，请重新登录");
        } else if (REASON_USER_DISABLED.equals(reason)) {
            result = Result.error(4012, "账号已被禁用，请联系管理员");
        } else {
            result = Result.error(401, "未登录或 Token 已过期");
        }

        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(401);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
