package com.novablog.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章实体类
 * 对应数据库 article 表
 */
@Data
public class Article {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 标题
     */
    private String title;

    /**
     * 正文（Markdown）
     */
    private String content;

    /**
     * 摘要
     */
    private String summary;

    /**
     * 封面图URL
     */
    private String cover;

    /**
     * 浏览量
     */
    private Long viewCount;

    /**
     * 点赞数
     */
    private Long likeCount;

    /**
     * 作者ID
     */
    private Long userId;

    /**
     * 分类ID
     */
    private Long categoryId;

    /**
     * 状态：1-已发布 0-草稿
     */
    private Integer status;

    /**
     * 向量索引状态：0-未索引 1-已索引 2-索引失败
     */
    private Integer indexed;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
