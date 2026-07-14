package com.novablog.common.constant;

/**
 * Redis Key 常量
 * 统一项目所有 Redis Key 前缀，避免多项目共用 Redis 时冲突
 */
public final class RedisKeyConstant {

    private RedisKeyConstant() {
        // 禁止实例化
    }

    /**
     * 项目级 Key 前缀
     */
    private static final String PREFIX = "blog:";

    /**
     * 文章浏览量 Key
     * 格式：blog:article:view:{articleId}
     */
    public static final String ARTICLE_VIEW = PREFIX + "article:view:";

    /**
     * 文章点赞计数 Key（String）
     * 格式：blog:article:like:count:{articleId}
     */
    public static final String ARTICLE_LIKE_COUNT = PREFIX + "article:like:count:";

    /**
     * 用户点赞状态 Key（String，值为 "1" 表示已赞）
     * 格式：blog:article:like:user:{userId}:{articleId}
     */
    public static final String ARTICLE_LIKE_USER_PREFIX = PREFIX + "article:like:user:";

    /**
     * 文章点赞用户集合 Key（旧版 Set，逐步淘汰）
     * 格式：blog:article:like:{articleId}
     */
    @Deprecated
    public static final String ARTICLE_LIKE = PREFIX + "article:like:";

    /**
     * 文章热门排行 ZSet Key
     */
    public static final String ARTICLE_HOT_ZSET = PREFIX + "article:hot:zset";

    /**
     * Token 黑名单 Key 前缀
     * 格式：blog:user:token:blacklist:{jti}
     */
    public static final String TOKEN_BLACKLIST = PREFIX + "user:token:blacklist:";

    /**
     * 浏览量同步定时任务分布式锁 Key
     */
    public static final String VIEW_SYNC_LOCK = PREFIX + "lock:view-sync";

    /**
     * 构建文章浏览量 Key
     *
     * @param articleId 文章ID
     * @return 完整 Redis Key
     */
    public static String articleView(Long articleId) {
        return ARTICLE_VIEW + articleId;
    }

    /**
     * 构建文章点赞用户集合 Key（旧版 Set）
     *
     * @param articleId 文章ID
     * @return 完整 Redis Key
     */
    @Deprecated
    public static String articleLike(Long articleId) {
        return ARTICLE_LIKE + articleId;
    }

    /**
     * 构建文章点赞计数 Key
     *
     * @param articleId 文章ID
     * @return 完整 Redis Key
     */
    public static String articleLikeCount(Long articleId) {
        return ARTICLE_LIKE_COUNT + articleId;
    }

    /**
     * 构建用户点赞状态 Key
     *
     * @param userId    用户ID
     * @param articleId 文章ID
     * @return 完整 Redis Key
     */
    public static String articleLikeUser(Long userId, Long articleId) {
        return ARTICLE_LIKE_USER_PREFIX + userId + ":" + articleId;
    }

    /**
     * 构建 Token 黑名单 Key
     *
     * @param jti Token ID
     * @return 完整 Redis Key
     */
    public static String tokenBlacklist(String jti) {
        return TOKEN_BLACKLIST + jti;
    }
}
