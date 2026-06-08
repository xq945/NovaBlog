package com.novablog.entity;

import lombok.Data;

/**
 * 文章-标签关联实体类
 * 对应数据库 article_tag 表
 */
@Data
public class ArticleTag {

    /**
     * 文章ID
     */
    private Long articleId;

    /**
     * 标签ID
     */
    private Long tagId;
}
