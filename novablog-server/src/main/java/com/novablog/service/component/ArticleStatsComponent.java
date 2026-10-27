package com.novablog.service.component;

import com.novablog.common.constant.RedisKeyConstant;
import com.novablog.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

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
     * 把文章标记为待同步，定时任务只处理有变化的文章
     *
     * <p>失败只告警：脏标记丢了最多让这次增量晚一轮同步，不影响业务请求
     */
    public void markDirty(Long articleId) {
        if (articleId == null) {
            return;
        }
        try {
            redisUtil.sAdd(RedisKeyConstant.ARTICLE_STATS_DIRTY, String.valueOf(articleId));
        } catch (Exception e) {
            log.warn("标记待同步文章失败, articleId={}", articleId, e);
        }
    }

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
            Long value = redisUtil.incr(RedisKeyConstant.articleView(articleId));
            markDirty(articleId);
            return value;
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
     * 写入点赞缓存（尽力而为，失败只告警）
     *
     * <p>点赞关系已在数据库落库，缓存写失败不能让请求失败；
     * Redis 丢数据后，用户再次点赞或下次启动重建都会补齐。
     */
    public void cacheLike(Long articleId, Long userId) {
        try {
            redisUtil.sAdd(RedisKeyConstant.articleLike(articleId), String.valueOf(userId));
            markDirty(articleId);
        } catch (Exception e) {
            log.warn("写入点赞缓存失败, articleId={}, userId={}", articleId, userId, e);
        }
    }

    /**
     * 移除点赞缓存（尽力而为，失败只告警）
     */
    public void cacheUnlike(Long articleId, Long userId) {
        try {
            redisUtil.sRem(RedisKeyConstant.articleLike(articleId), String.valueOf(userId));
            markDirty(articleId);
        } catch (Exception e) {
            log.warn("移除点赞缓存失败, articleId={}, userId={}", articleId, userId, e);
        }
    }

    /**
     * 获取文章点赞数
     *
     * @param articleId 文章ID
     * @return 点赞数；缓存缺失或读取失败返回 null，交由调用方回落到数据库计数
     */
    public Long getLikeCount(Long articleId) {
        try {
            String key = RedisKeyConstant.articleLike(articleId);
            Long count = redisUtil.sCard(key);
            // SCARD 对不存在的键也返回 0，用 EXISTS 区分「真没人点赞」与「缓存缺失」
            if (count != null && count == 0 && !Boolean.TRUE.equals(redisUtil.hasKey(key))) {
                return null;
            }
            return count;
        } catch (Exception e) {
            log.warn("从 Redis 读取点赞数失败, articleId={}", articleId, e);
            return null;
        }
    }

    /**
     * 判断用户是否已点赞
     *
     * @return true-已点赞，false-未点赞，null-缓存不可用（调用方应回落数据库）
     */
    public Boolean isLiked(Long articleId, Long userId) {
        try {
            String key = RedisKeyConstant.articleLike(articleId);
            Boolean liked = redisUtil.sIsMember(key, String.valueOf(userId));
            if (Boolean.TRUE.equals(liked)) {
                return true;
            }
            // 键不存在时无法判断是「没点赞」还是「缓存丢了」，
            // 返回 null 让调用方回落关系表
            if (!Boolean.TRUE.equals(redisUtil.hasKey(key))) {
                return null;
            }
            return false;
        } catch (Exception e) {
            log.warn("查询点赞状态失败, articleId={}, userId={}", articleId, userId, e);
            return null;
        }
    }

    /**
     * 读取某篇文章的点赞缓存成员
     *
     * @return 用户ID列表；Redis 不可用返回 null（与「缓存为空」区分）
     */
    public List<Long> getLikeMembers(Long articleId) {
        try {
            Set<String> members = redisUtil.sMembers(RedisKeyConstant.articleLike(articleId));
            if (members == null || members.isEmpty()) {
                return new ArrayList<>();
            }
            List<Long> userIds = new ArrayList<>(members.size());
            for (String member : members) {
                try {
                    userIds.add(Long.parseLong(member));
                } catch (NumberFormatException ignored) {
                    // 非法成员直接丢弃
                }
            }
            return userIds;
        } catch (Exception e) {
            log.warn("读取点赞缓存成员失败, articleId={}", articleId, e);
            return null;
        }
    }

    /**
     * 用关系表数据整体重建某篇文章的点赞缓存
     */
    public void resetLikeCache(Long articleId, Collection<Long> userIds) {
        String key = RedisKeyConstant.articleLike(articleId);
        try {
            redisUtil.del(key);
            if (userIds == null || userIds.isEmpty()) {
                return;
            }
            List<String> members = new ArrayList<>(userIds.size());
            for (Long userId : userIds) {
                members.add(String.valueOf(userId));
            }
            redisUtil.sAddAll(key, members);
        } catch (Exception e) {
            log.warn("重建点赞缓存失败, articleId={}", articleId, e);
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
     * 浏览量 +1 对应的热度分增量
     */
    public void addViewToHotScore(Long articleId) {
        incrementHotScore(articleId, VIEW_WEIGHT);
    }

    /**
     * 新增一次点赞对应的热度分增量
     */
    public void addLikeToHotScore(Long articleId) {
        incrementHotScore(articleId, LIKE_WEIGHT);
    }

    /**
     * 取消一次点赞对应的热度分增量
     */
    public void removeLikeFromHotScore(Long articleId) {
        incrementHotScore(articleId, -LIKE_WEIGHT);
    }

    /**
     * 以增量方式修正热度分
     *
     * <p>并发请求各自持有不同的浏览/点赞快照，用 ZADD 写绝对值会让后写者
     * 把较大的分数覆盖成较小值；ZINCRBY 只做累加，不会回退。
     */
    public void incrementHotScore(Long articleId, double delta) {
        if (articleId == null || delta == 0) {
            return;
        }
        try {
            redisUtil.zIncrBy(RedisKeyConstant.ARTICLE_HOT_ZSET, String.valueOf(articleId), delta);
        } catch (Exception e) {
            log.warn("增量更新热门排行失败, articleId={}, delta={}", articleId, delta, e);
        }
    }

    /**
     * 获取文章当前热度分
     *
     * @return 分数，成员不存在或读取失败返回 null
     */
    public Double getHotScore(Long articleId) {
        if (articleId == null) {
            return null;
        }
        try {
            return redisUtil.zScore(RedisKeyConstant.ARTICLE_HOT_ZSET, String.valueOf(articleId));
        } catch (Exception e) {
            log.warn("读取热门排行分数失败, articleId={}", articleId, e);
            return null;
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
