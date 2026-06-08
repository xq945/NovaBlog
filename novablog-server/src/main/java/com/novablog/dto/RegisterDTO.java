package com.novablog.dto;

import lombok.Data;

/**
 * 用户注册请求参数
 */
@Data
public class RegisterDTO {

    /**
     * 用户名，3-20位字母/数字/下划线
     */
    private String username;

    /**
     * 昵称，1-20位
     */
    private String nickname;

    /**
     * 密码，8-20位，必须包含大小写字母、数字、特殊符号
     */
    private String password;
}
