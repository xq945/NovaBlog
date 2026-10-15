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
     * 文章点赞用户集合 Key
     * 格式：blog:article:like:{articleId}
     */
    public static final String ARTICLE_LIKE = PREFIX + "article:like:";

    /**
     * 文章热门排行 ZSet Key
     */
    public static final String ARTICLE_HOT_ZSET = PREFIX + "article:hot:zset";

    /**
     * Token 黑名单 Key 前缀
     * 格式：blog:user:token:blacklist:{jti}
     * 存放已被消费或已失效的单个令牌（登出的 AT、轮换后的旧 RT）
     */
    public static final String TOKEN_BLACKLIST = PREFIX + "user:token:blacklist:";

    /**
     * 会话撤销 Key 前缀
     * 格式：blog:user:token:session:{sid}
     * 值为撤销时间戳，命中即整个会话（该 sid 的全部 AT/RT）失效
     */
    public static final String TOKEN_SESSION = PREFIX + "user:token:session:";

    /**
     * RefreshToken 轮换映射 Key 前缀
     * 格式：blog:user:token:rotation:{oldJti}
     * 值为轮换后签发的新 RT，仅在宽限窗口内存在，用于收敛并发刷新
     */
    public static final String TOKEN_ROTATION = PREFIX + "user:token:rotation:";

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
     * 构建文章点赞用户集合 Key
     *
     * @param articleId 文章ID
     * @return 完整 Redis Key
     */
    public static String articleLike(Long articleId) {
        return ARTICLE_LIKE + articleId;
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

    /**
     * 构建会话撤销 Key
     *
     * @param sid 会话标识
     * @return 完整 Redis Key
     */
    public static String tokenSession(String sid) {
        return TOKEN_SESSION + sid;
    }

    /**
     * 构建轮换映射 Key
     *
     * @param oldJti 被轮换掉的旧 RefreshToken 的 jti
     * @return 完整 Redis Key
     */
    public static String tokenRotation(String oldJti) {
        return TOKEN_ROTATION + oldJti;
    }
}
