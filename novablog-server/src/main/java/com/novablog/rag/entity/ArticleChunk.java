package com.novablog.rag.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章向量分片实体
 * 对应 article_chunk 表
 */
@Data
public class ArticleChunk {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 文章ID
     */
    private Long articleId;

    /**
     * 片段序号
     */
    private Integer chunkIndex;

    /**
     * 纯文本片段内容
     */
    private String content;

    /**
     * 向量数组（Float32列表）
     */
    private String embedding;

    /**
     * 片段token估算数
     */
    private Integer tokenCount;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
