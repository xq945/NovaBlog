package com.novablog.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户公开主页信息 VO
 */
@Data
public class UserProfileVO {

    private Long id;

    private String username;

    private String nickname;

    private String avatar;

    private String bio;

    private Long articleCount;

    private LocalDateTime joinTime;
}
