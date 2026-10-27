package com.novablog.vo;

import lombok.Data;

/**
 * 登录设备视图对象
 */
@Data
public class SessionVO {

    /**
     * 会话标识，踢出该设备时回传
     */
    private String sid;

    /**
     * 登录时记录的客户端 IP
     */
    private String ip;

    /**
     * 由 User-Agent 推导出的设备描述，如「Chrome / Windows」
     */
    private String deviceName;

    /**
     * 登录时间（毫秒时间戳）
     */
    private Long loginTime;

    /**
     * 最后活跃时间（毫秒时间戳）
     */
    private Long lastActive;

    /**
     * 是否为发起本次请求的设备
     */
    private Boolean current;
}
