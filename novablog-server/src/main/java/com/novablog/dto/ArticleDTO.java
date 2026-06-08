package com.novablog.dto;

import lombok.Data;

import java.util.List;

/**
 * 文章发布/修改请求 DTO
 */
@Data
public class ArticleDTO {

    /**
     * 文章ID（修改时必填）
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
     * 摘要（为空时自动生成）
     */
    private String summary;

    /**
     * 封面URL
     */
    private String cover;

    /**
     * 分类ID
     */
    private Long categoryId;

    /**
     * 标签ID列表
     */
    private List<Long> tagIds;

    /**
     * 状态：1-已发布（默认） 0-草稿
     */
    private Integer status;
}
