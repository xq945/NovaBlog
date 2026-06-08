package com.novablog.vo;

import lombok.Data;

/**
 * 点赞状态 VO
 */
@Data
public class LikeStatusVO {

    /**
     * 当前用户是否点赞
     */
    private Boolean liked;

    /**
     * 点赞总数
     */
    private Long likeCount;
}
