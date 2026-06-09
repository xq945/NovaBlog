package com.novablog.dto;

import lombok.Data;

/**
 * 文章标签关联 DTO
 * 用于批量查询文章对应的标签
 */
@Data
public class ArticleTagDTO {

    /**
     * 文章ID
     */
    private Long articleId;

    /**
     * 标签ID
     */
    private Long tagId;

    /**
     * 标签名称
     */
    private String tagName;
}
