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

    /**
     * 粉丝数
     */
    private Long followerCount;

    /**
     * 关注数
     */
    private Long followingCount;

    /**
     * 当前登录用户是否已关注该用户；匿名或自己看自己为 false
     */
    private Boolean followed;

    private LocalDateTime joinTime;
}
