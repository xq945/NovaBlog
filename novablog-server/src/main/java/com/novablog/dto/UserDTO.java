package com.novablog.dto;

import lombok.Data;

/**
 * 用户信息 DTO
 * 用于 ThreadLocal 中传递当前登录用户信息
 */
@Data
public class UserDTO {

    /**
     * 用户ID
     */
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 角色
     */
    private String role;
}
