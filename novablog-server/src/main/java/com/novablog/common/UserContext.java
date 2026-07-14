package com.novablog.common;

import java.util.List;

/**
 * 用户上下文工具类。基于 ThreadLocal 实现用户信息共享。
 * JwtAuthenticationFilter 在认证成功后设置，请求结束后由 Filter 清理。
 */
public class UserContext {

    private static final ThreadLocal<Long> userIdHolder = new ThreadLocal<>();
    private static final ThreadLocal<String> usernameHolder = new ThreadLocal<>();
    private static final ThreadLocal<List<String>> rolesHolder = new ThreadLocal<>();

    public static void set(Long userId, String username, List<String> roles) {
        userIdHolder.set(userId);
        usernameHolder.set(username);
        rolesHolder.set(roles);
    }

    public static Long getUserId() {
        return userIdHolder.get();
    }

    public static String getUsername() {
        return usernameHolder.get();
    }

    public static List<String> getRoles() {
        return rolesHolder.get();
    }

    public static String getRole() {
        List<String> roles = rolesHolder.get();
        if (roles == null || roles.isEmpty()) {
            return null;
        }
        // 取第一个角色，去掉 ROLE_ 前缀返回（兼容旧代码）
        String role = roles.get(0);
        return role.startsWith("ROLE_") ? role.substring(5) : role;
    }

    public static void clear() {
        userIdHolder.remove();
        usernameHolder.remove();
        rolesHolder.remove();
    }
}
