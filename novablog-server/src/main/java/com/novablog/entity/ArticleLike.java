package com.novablog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章点赞关系
 *
 * <p>点赞关系的正本。Redis Set 只是「谁点过」的缓存，用于 O(1) 判重与计数展示；
 * Redis 重启或数据被淘汰后，启动时会按本表重建缓存。
 */
@Data
@TableName("article_like")
public class ArticleLike {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long articleId;

    private LocalDateTime createTime;
}
