package com.novablog.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 请求信息工具类
 * 集中提取客户端 IP 与 User-Agent，避免各处重复实现导致口径不一致
 */
public final class RequestUtils {

    /**
     * User-Agent 截断长度，与 login_log.user_agent 字段长度一致
     */
    private static final int UA_MAX_LENGTH = 500;

    private RequestUtils() {
        // 禁止实例化
    }

    /**
     * 获取当前线程绑定的请求
     *
     * @return 当前请求；非 Web 上下文（如测试、定时任务）返回 null
     */
    public static HttpServletRequest currentRequest() {
        try {
            ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            return attributes.getRequest();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取客户端真实 IP
     * 经反向代理时 X-Forwarded-For 形如「客户端, 代理1, 代理2」，取第一个
     *
     * @param request 请求
     * @return 客户端 IP，无法获取时返回 null
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (isValidIp(ip)) {
            int comma = ip.indexOf(',');
            return comma > 0 ? ip.substring(0, comma).trim() : ip.trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (isValidIp(ip)) {
            return ip.trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * 获取 User-Agent，超长时截断到字段长度
     *
     * @param request 请求
     * @return User-Agent，不存在时返回 null
     */
    public static String getUserAgent(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String ua = request.getHeader("User-Agent");
        if (ua != null && ua.length() > UA_MAX_LENGTH) {
            return ua.substring(0, UA_MAX_LENGTH);
        }
        return ua;
    }

    private static boolean isValidIp(String ip) {
        return ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip);
    }
}
