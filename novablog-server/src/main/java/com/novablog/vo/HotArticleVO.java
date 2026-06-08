package com.novablog.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 热门文章 VO
 * 继承 ArticleVO，增加热度分字段
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class HotArticleVO extends ArticleVO {

    /**
     * 热度分
     */
    private Double hotScore;
}
