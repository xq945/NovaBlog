package com.novablog.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员评论列表 VO
 * 增加文章标题字段，方便管理员定位评论所属文章
 */
@Data
public class AdminCommentVO {

    /**
     * 评论ID
     */
    private Long id;

    /**
     * 评论内容
     */
    private String content;

    /**
     * 所属文章ID
     */
    private Long articleId;

    /**
     * 所属文章标题
     */
    private String articleTitle;

    /**
     * 评论者信息
     */
    private UserVO user;

    /**
     * 父评论ID（一级评论为 null）
     */
    private Long parentId;

    /**
     * 被回复者信息
     */
    private UserVO replyToUser;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
