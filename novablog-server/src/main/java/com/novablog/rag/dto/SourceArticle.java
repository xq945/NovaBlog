package com.novablog.rag.dto;

import lombok.Data;

/**
 * 回答引用的来源文章
 */
@Data
public class SourceArticle {

    /**
     * 文章ID
     */
    private Long id;

    /**
     * 文章标题
     */
    private String title;

    /**
     * 文章摘要
     */
    private String summary;

    /**
     * 相关片段内容
     */
    private String snippet;

    /**
     * 相似度分数（余弦相似度，-1 到 1）
     */
    private Double score;
}
