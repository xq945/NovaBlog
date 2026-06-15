package com.novablog.interceptor;

import com.novablog.common.Result;
import com.novablog.common.UserContext;
import com.novablog.dto.UserDTO;
import com.novablog.entity.User;
import com.novablog.mapper.UserMapper;
import com.novablog.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 认证拦截器
 * 校验请求头中的 Access Token，并实时校验用户状态
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        String method = request.getMethod();

        // 公开接口：GET /article/{id}（文章详情，不强制登录，但如有 Token 需解析供 Service 层判断）
        if ("GET".equals(method) && uri.matches("/article/\\d+")) {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                try {
                    String token = authHeader.substring(7);
                    UserDTO user = jwtUtil.getUserFromToken(token);
                    UserContext.set(user);
                } catch (Exception e) {
                    // Token 无效或过期，忽略，作为未登录用户处理
                }
            }
            return true;
        }

        // 1. 从请求头提取 Token
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeErrorResponse(response, 401, "请先登录");
            return false;
        }

        String token = authHeader.substring(7);

        // 2. 解析并验证 Token
        try {
            // 验证 Token 类型必须是 access
            String tokenType = jwtUtil.getTokenType(token);
            if (!"access".equals(tokenType)) {
                writeErrorResponse(response, 401, "Token 类型错误");
                return false;
            }

            // 提取用户信息存入 ThreadLocal
            UserDTO user = jwtUtil.getUserFromToken(token);
            UserContext.set(user);

            // 实时校验用户状态（防止被禁用后 token 仍可用）
            User dbUser = userMapper.findById(user.getId());
            if (dbUser == null || dbUser.getStatus() == 0) {
                writeErrorResponse(response, 401, "用户不存在或已被禁用");
                return false;
            }

            return true;

        } catch (ExpiredJwtException e) {
            writeErrorResponse(response, 401, "Token 已过期，请重新登录");
            return false;
        } catch (JwtException e) {
            writeErrorResponse(response, 401, "Token 无效");
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 请求结束后清理 ThreadLocal，防止内存泄漏
        UserContext.clear();
    }

    /**
     * 向响应中写入错误信息
     */
    private void writeErrorResponse(HttpServletResponse response, int code, String message) throws Exception {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_OK);
        String json = objectMapper.writeValueAsString(Result.error(code, message));
        response.getWriter().write(json);
    }
}
