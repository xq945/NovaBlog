package com.novablog.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评论列表项 VO
 * 包含嵌套的二级回复列表
 */
@Data
public class CommentVO {

    /**
     * 评论ID
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
     * 父评论ID（一级评论为 null）
     */
    private Long parentId;

    /**
     * 评论者信息
     */
    private UserVO user;

    /**
     * 被回复者信息（为 null 时不展示）
     */
    private UserVO replyToUser;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 二级回复列表（Service 层初始化为空列表）
     */
    private List<CommentVO> children;
}
