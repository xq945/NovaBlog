package com.novablog.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评论实体类
 * 对应数据库 comment 表
 */
@Data
public class Comment {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 评论内容
     */
    private String content;

    /**
     * 文章ID
     */
    private Long articleId;

    /**
     * 评论者ID
     */
    private Long userId;

    /**
     * 父评论ID（一级评论为 null）
     */
    private Long parentId;

    /**
     * 被回复用户ID
     */
    private Long replyToId;

    /**
     * 状态：1-正常 0-已删除
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
