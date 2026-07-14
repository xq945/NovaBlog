package com.novablog.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 收藏文章 VO
 * 包含文章基本信息和收藏时间
 */
@Data
public class FavoriteArticleVO {

    private Long id;

    private String title;

    private String summary;

    private String cover;

    private Integer viewCount;

    private Integer likeCount;

    private UserVO author;

    private CategoryVO category;

    private LocalDateTime createTime;

    /** 收藏时间 */
    private LocalDateTime favoriteTime;
}
