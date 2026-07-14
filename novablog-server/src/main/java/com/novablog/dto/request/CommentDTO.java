package com.novablog.dto.request;

import lombok.Data;

/**
 * 发表评论请求 DTO
 */
@Data
public class CommentDTO {

    /**
     * 文章ID
     */
    private Long articleId;

    /**
     * 评论内容
     */
    private String content;

    /**
     * 父评论ID（回复时必填，指向一级评论）
     */
    private Long parentId;

    /**
     * 被回复用户ID（回复时必填，后端可自动推断）
     */
    private Long replyToId;
}
