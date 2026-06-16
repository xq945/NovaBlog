package com.novablog.rag.event;

import lombok.Getter;

/**
 * 文章发布/更新事件
 * 用于触发异步向量索引
 */
@Getter
public class ArticlePublishedEvent {

    /**
     * 文章ID
     */
    private final Long articleId;

    /**
     * 是否为新建文章
     */
    private final boolean newlyCreated;

    public ArticlePublishedEvent(Long articleId, boolean newlyCreated) {
        this.articleId = articleId;
        this.newlyCreated = newlyCreated;
    }
}
