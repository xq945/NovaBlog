package com.novablog.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章列表项 VO
 */
@Data
public class ArticleVO {

    /**
     * 文章ID
     */
    private Long id;

    /**
     * 标题
     */
    private String title;

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
    private Integer viewCount;

    /**
     * 点赞数
     */
    private Integer likeCount;

    /**
     * 状态：1-已发布 0-草稿
     */
    private Integer status;

    /**
     * 作者信息
     */
    private UserVO author;

    /**
     * 分类信息
     */
    private CategoryVO category;

    /**
     * 标签名称列表
     */
    private List<String> tags;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
