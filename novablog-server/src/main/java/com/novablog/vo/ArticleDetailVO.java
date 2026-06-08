package com.novablog.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章详情 VO
 * 继承 ArticleVO 增加 content 和 updateTime
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ArticleDetailVO extends ArticleVO {

    /**
     * 正文（Markdown）
     */
    private String content;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 标签完整信息（含ID）
     */
    private List<TagVO> tagList;
}
