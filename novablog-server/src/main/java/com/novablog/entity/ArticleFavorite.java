package com.novablog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章收藏关系
 *
 * <p>与点赞不同，收藏关系落在 MySQL：用户需要「我的收藏」列表，
 * 且这份关系不能随 Redis 重启丢失。Redis 在这里不承担任何正本职责。
 */
@Data
@TableName("article_favorite")
public class ArticleFavorite {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long articleId;

    private LocalDateTime createTime;
}
