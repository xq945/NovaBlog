package com.novablog.dto;

import lombok.Data;

/**
 * 修改个人信息请求参数
 */
@Data
public class UpdateProfileDTO {

    /**
     * 用户名，可选，修改时必须符合规则（3-20位，字母/数字/下划线）
     */
    private String username;

    /**
     * 昵称，必填，1-20位
     */
    private String nickname;

    /**
     * 邮箱，可选，长度不超过100位
     */
    private String email;

    /**
     * 头像URL，可选
     */
    private String avatar;

    /**
     * 密码，可选，修改时必须符合复杂度规则（8-20位，包含大小写字母、数字、特殊符号）
     */
    private String password;
}
