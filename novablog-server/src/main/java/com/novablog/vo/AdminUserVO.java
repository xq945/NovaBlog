package com.novablog.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员用户列表 VO
 * 不含 password 字段，可直接序列化返回
 */
@Data
public class AdminUserVO {

    /**
     * 用户ID
     */
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像URL
     */
    private String avatar;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 角色
     */
    private String role;

    /**
     * 状态：1-启用，0-禁用
     */
    private Integer status;

    /**
     * 注册时间
     */
    private LocalDateTime createTime;
}
