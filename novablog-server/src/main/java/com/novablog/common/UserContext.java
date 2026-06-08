package com.novablog.common;

import com.novablog.dto.UserDTO;

/**
 * 用户上下文工具类
 * 基于 ThreadLocal 实现同一线程内的用户信息共享
 */
public class UserContext {

    private static final ThreadLocal<UserDTO> HOLDER = new ThreadLocal<>();

    /**
     * 设置当前用户
     */
    public static void set(UserDTO user) {
        HOLDER.set(user);
    }

    /**
     * 获取当前用户
     */
    public static UserDTO get() {
        return HOLDER.get();
    }

    /**
     * 获取当前用户ID
     */
    public static Long getUserId() {
        UserDTO user = get();
        return user != null ? user.getId() : null;
    }

    /**
     * 获取当前用户名
     */
    public static String getUsername() {
        UserDTO user = get();
        return user != null ? user.getUsername() : null;
    }

    /**
     * 获取当前用户角色
     */
    public static String getRole() {
        UserDTO user = get();
        return user != null ? user.getRole() : null;
    }

    /**
     * 清理当前线程的用户信息
     * 必须在请求结束后调用，防止内存泄漏
     */
    public static void clear() {
        HOLDER.remove();
    }
}
