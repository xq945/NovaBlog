package com.novablog.vo;

import lombok.Data;

/**
 * 关注状态。关注/取关都返回权威状态，前端不做本地猜数
 */
@Data
public class FollowStatusVO {

    private Boolean followed;

    /**
     * 被关注者的粉丝数
     */
    private Long followerCount;
}
