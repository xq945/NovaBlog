package com.novablog.dto;

import lombok.Data;

/**
 * 修改个人信息请求参数
 */
@Data
public class UpdateProfileDTO {

    /**
     * 昵称，必填，1-20位
     */
    private String nickname;

    /**
     * 邮箱，可选，长度不超过100位
     */
    private String email;
}
