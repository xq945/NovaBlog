package com.novablog.rag.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novablog.common.Result;
import com.novablog.common.UserContext;
import com.novablog.config.AiProperties;
import com.novablog.util.RedisUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

/**
 * RAG 问答接口限流拦截器
 * 对公开接口按 IP 限流，对登录接口按用户 ID 限流
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagRateLimitInterceptor implements HandlerInterceptor {

    private final RedisUtil redisUtil;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Redis Key 前缀
     */
    private static final String RATE_LIMIT_KEY_PREFIX = "blog:rag:rate:";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        String method = request.getMethod();

        // 只处理 RAG/Chat 问答接口
        boolean isPublicAsk = ("POST".equalsIgnoreCase(method) && "/rag/ask".equals(uri))
                || ("GET".equalsIgnoreCase(method) && "/rag/ask/stream".equals(uri));
        boolean isChatAsk = ("POST".equalsIgnoreCase(method) && "/chat/ask".equals(uri))
                || ("GET".equalsIgnoreCase(method) && "/chat/ask/stream".equals(uri));

        if (!isPublicAsk && !isChatAsk) {
            return true;
        }

        // AI 功能未启用时不限流
        if (!aiProperties.isEnabled()) {
            return true;
        }

        String key;
        if (isChatAsk && UserContext.getUserId() != null) {
            key = RATE_LIMIT_KEY_PREFIX + "user:" + UserContext.getUserId();
        } else {
            key = RATE_LIMIT_KEY_PREFIX + getClientIp(request);
        }

        int limit = aiProperties.getRag().getRateLimitPerMinute();
        if (limit <= 0) {
            return true;
        }

        // 使用 Redis 计数，窗口 60 秒
        Long current = redisUtil.incr(key);
        if (current != null && current == 1) {
            redisUtil.setWithExpire(key, "1", 60, TimeUnit.SECONDS);
        }

        if (current != null && current > limit) {
            log.warn("RAG 接口触发限流, key={}, count={}", key, current);
            writeErrorResponse(response, 429, "请求过于频繁，请稍后再试");
            return false;
        }

        return true;
    }

    /**
     * 获取客户端真实 IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "unknown";
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
