package com.novablog.dto;

import lombok.Data;

/**
 * 修改用户状态请求参数
 */
@Data
public class UserStatusDTO {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 状态：1-启用，0-禁用
     */
    private Integer status;
}
