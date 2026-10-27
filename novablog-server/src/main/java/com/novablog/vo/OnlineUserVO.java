package com.novablog.vo;

import lombok.Data;

/**
 * 在线用户视图对象（后台管理用）
 */
@Data
public class OnlineUserVO {

    private Long userId;

    private String username;

    private String nickname;

    private String avatar;

    /**
     * 该用户当前有效的登录设备数
     */
    private Integer deviceCount;

    /**
     * 最后活跃时间（毫秒时间戳）
     */
    private Long lastActive;
}
