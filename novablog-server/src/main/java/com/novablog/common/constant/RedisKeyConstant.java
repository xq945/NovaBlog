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
     * 用户会话索引 ZSet Key 前缀
     * 格式：blog:user:sessions:{userId}
     * member 为 sid，score 为登录时间，用于枚举该用户的全部活跃会话并按登录先后淘汰
     */
    public static final String SESSION_INDEX = PREFIX + "user:sessions:";

    /**
     * 会话元信息 Key 前缀
     * 格式：blog:user:session:meta:{sid}
     * 值为 JSON（IP、UA、登录时间），生命周期与令牌家族对齐
     */
    public static final String SESSION_META = PREFIX + "user:session:meta:";

    /**
     * 会话最后活跃时间 Key 前缀
     * 格式：blog:user:session:active:{sid}
     */
    public static final String SESSION_ACTIVE = PREFIX + "user:session:active:";

    /**
     * 会话活跃度更新节流 Key 前缀
     * 格式：blog:user:session:throttle:{sid}
     * 仅作为 SET NX 标记，避免每个请求都写一次活跃时间
     */
    public static final String SESSION_THROTTLE = PREFIX + "user:session:throttle:";

    /**
     * 在线用户 ZSet Key
     * member 为 userId，score 为最后活跃时间戳，按时间窗口判定是否在线
     */
    public static final String ONLINE_USERS = PREFIX + "user:online:zset";

    /**
     * 未读通知数缓存 Key 前缀
     * 格式：blog:notify:unread:{userId}
     *
     * <p>这只是 60 秒的读缓存，正本是数据库的 COUNT(*)。
     * 任何写通知、标记已读的操作都直接删除该 key，而不是做加减。
     */
    public static final String NOTIFY_UNREAD = PREFIX + "notify:unread:";

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

    /**
     * 构建用户会话索引 Key
     *
     * @param userId 用户ID
     * @return 完整 Redis Key
     */
    public static String sessionIndex(Long userId) {
        return SESSION_INDEX + userId;
    }

    /**
     * 构建会话元信息 Key
     *
     * @param sid 会话标识
     * @return 完整 Redis Key
     */
    public static String sessionMeta(String sid) {
        return SESSION_META + sid;
    }

    /**
     * 构建会话活跃时间 Key
     *
     * @param sid 会话标识
     * @return 完整 Redis Key
     */
    public static String sessionActive(String sid) {
        return SESSION_ACTIVE + sid;
    }

    /**
     * 构建会话节流标记 Key
     *
     * @param sid 会话标识
     * @return 完整 Redis Key
     */
    public static String sessionThrottle(String sid) {
        return SESSION_THROTTLE + sid;
    }

    /**
     * 构建未读通知数缓存 Key
     *
     * @param userId 用户ID
     * @return 完整 Redis Key
     */
    public static String notifyUnread(Long userId) {
        return NOTIFY_UNREAD + userId;
    }
}
