package com.novablog.service.component;

import com.novablog.common.constant.RedisKeyConstant;
import com.novablog.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 文章统计组件
 * 封装文章浏览量、点赞数、热度分与 Redis 的交互，供 ArticleService 复用
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArticleStatsComponent {

    private final RedisUtil redisUtil;

    /**
     * 热度分计算权重：浏览量权重
     */
    private static final double VIEW_WEIGHT = 1.0;

    /**
     * 热度分计算权重：点赞数权重
     */
    private static final double LIKE_WEIGHT = 3.0;

    /**
     * 初始化文章浏览量
     *
     * @param articleId 文章ID
     */
    public void initViewCount(Long articleId) {
        try {
            redisUtil.set(RedisKeyConstant.articleView(articleId), "0");
        } catch (Exception e) {
            log.warn("初始化 Redis 浏览量失败, articleId={}", articleId, e);
        }
    }

    /**
     * 增加文章浏览量
     *
     * @param articleId 文章ID
     * @return 增加后的浏览量，失败返回 null
     */
    public Long incrementViewCount(Long articleId) {
        try {
            return redisUtil.incr(RedisKeyConstant.articleView(articleId));
        } catch (Exception e) {
            log.warn("Redis 浏览量自增失败, articleId={}", articleId, e);
            return null;
        }
    }

    /**
     * 获取文章浏览量
     *
     * @param articleId 文章ID
     * @return 浏览量，不存在或失败返回 null
     */
    public Long getViewCount(Long articleId) {
        try {
            String value = redisUtil.get(RedisKeyConstant.articleView(articleId));
            return value != null ? Long.parseLong(value) : null;
        } catch (Exception e) {
            log.warn("从 Redis 读取浏览量失败, articleId={}", articleId, e);
            return null;
        }
    }

    /**
     * 删除文章浏览量
     *
     * @param articleId 文章ID
     */
    public void deleteViewCount(Long articleId) {
        try {
            redisUtil.del(RedisKeyConstant.articleView(articleId));
        } catch (Exception e) {
            log.warn("删除 Redis 浏览量失败, articleId={}", articleId, e);
        }
    }

    /**
     * 用户点赞文章
     *
     * @param articleId 文章ID
     * @param userId    用户ID
     * @return true-点赞成功，false-已点赞
     */
    public boolean like(Long articleId, Long userId) {
        try {
            Long result = redisUtil.sAdd(RedisKeyConstant.articleLike(articleId), String.valueOf(userId));
            return result != null && result > 0;
        } catch (Exception e) {
            log.warn("Redis 点赞失败, articleId={}, userId={}", articleId, userId, e);
            return false;
        }
    }

    /**
     * 用户取消点赞
     *
     * @param articleId 文章ID
     * @param userId    用户ID
     * @return true-取消成功，false-未点赞
     */
    public boolean unlike(Long articleId, Long userId) {
        try {
            Long result = redisUtil.sRem(RedisKeyConstant.articleLike(articleId), String.valueOf(userId));
            return result != null && result > 0;
        } catch (Exception e) {
            log.warn("Redis 取消点赞失败, articleId={}, userId={}", articleId, userId, e);
            return false;
        }
    }

    /**
     * 获取文章点赞数
     *
     * @param articleId 文章ID
     * @return 点赞数，失败返回 null
     */
    public Long getLikeCount(Long articleId) {
        try {
            return redisUtil.sCard(RedisKeyConstant.articleLike(articleId));
        } catch (Exception e) {
            log.warn("从 Redis 读取点赞数失败, articleId={}", articleId, e);
            return null;
        }
    }

    /**
     * 判断用户是否已点赞
     *
     * @param articleId 文章ID
     * @param userId    用户ID
     * @return true-已点赞，false-未点赞或查询失败
     */
    public boolean isLiked(Long articleId, Long userId) {
        try {
            Boolean liked = redisUtil.sIsMember(RedisKeyConstant.articleLike(articleId), String.valueOf(userId));
            return liked != null && liked;
        } catch (Exception e) {
            log.warn("查询点赞状态失败, articleId={}, userId={}", articleId, userId, e);
            return false;
        }
    }

    /**
     * 删除文章点赞集合
     *
     * @param articleId 文章ID
     */
    public void deleteLikeSet(Long articleId) {
        try {
            redisUtil.del(RedisKeyConstant.articleLike(articleId));
        } catch (Exception e) {
            log.warn("删除 Redis 点赞集合失败, articleId={}", articleId, e);
        }
    }

    /**
     * 更新文章热度分
     *
     * @param articleId 文章ID
     * @param viewCount 浏览量
     * @param likeCount 点赞数
     */
    public void updateHotScore(Long articleId, Long viewCount, Long likeCount) {
        if (articleId == null) {
            return;
        }
        try {
            long safeViewCount = viewCount != null ? viewCount : 0L;
            long safeLikeCount = likeCount != null ? likeCount : 0L;
            double hotScore = safeViewCount * VIEW_WEIGHT + safeLikeCount * LIKE_WEIGHT;
            redisUtil.zAdd(RedisKeyConstant.ARTICLE_HOT_ZSET, String.valueOf(articleId), hotScore);
        } catch (Exception e) {
            log.warn("更新热门排行 ZSet 失败, articleId={}", articleId, e);
        }
    }

    /**
     * 从热门排行中移除文章
     *
     * @param articleId 文章ID
     */
    public void removeFromHot(Long articleId) {
        try {
            redisUtil.zRem(RedisKeyConstant.ARTICLE_HOT_ZSET, String.valueOf(articleId));
        } catch (Exception e) {
            log.warn("从热门排行移除失败, articleId={}", articleId, e);
        }
    }

    /**
     * 计算热度分
     *
     * @param viewCount 浏览量
     * @param likeCount 点赞数
     * @return 热度分
     */
    public double calculateHotScore(Long viewCount, Long likeCount) {
        long safeViewCount = viewCount != null ? viewCount : 0L;
        long safeLikeCount = likeCount != null ? likeCount : 0L;
        return safeViewCount * VIEW_WEIGHT + safeLikeCount * LIKE_WEIGHT;
    }
}
