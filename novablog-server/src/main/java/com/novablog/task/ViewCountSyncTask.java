package com.novablog.task;

import com.novablog.common.constant.RedisKeyConstant;
import com.novablog.entity.Article;
import com.novablog.mapper.ArticleLikeMapper;
import com.novablog.mapper.ArticleMapper;
import com.novablog.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 文章统计数据同步定时任务
 *
 * <p>只处理「脏集合」里的文章：浏览量、点赞数变化时写入方会 SADD 文章 ID，
 * 任务用 SPOP 原子取出并回写 MySQL，不再全量扫描所有统计 key。
 *
 * <p>三条安全规则：
 * <ul>
 *   <li>浏览量只增不减：Redis 值小于数据库值时，用数据库值回填 Redis，
 *       避免 Redis 丢数据后整点同步把历史浏览量改小</li>
 *   <li>点赞数以 article_like 关系表为正本，按 COUNT(*) 校准，不依赖 Redis 缓存；
 *       明细为空时跳过，避免覆盖升级前无法回填的历史计数</li>
 *   <li>单批失败降级逐条；仍失败的重新入队，下一轮再试</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ViewCountSyncTask {

    private final RedisUtil redisUtil;
    private final ArticleMapper articleMapper;
    private final ArticleLikeMapper articleLikeMapper;

    /**
     * 分布式锁超时时间：10分钟
     */
    private static final Duration LOCK_TIMEOUT = Duration.ofMinutes(10);

    /**
     * 单条 UPDATE 的参数数量随文章数线性增长，按 500 一篇切分
     */
    private static final int BATCH_SIZE = 500;

    /**
     * 单轮最多处理的文章数，避免任务时长失控
     */
    private static final int MAX_ARTICLES_PER_RUN = 2000;

    /**
     * 原子释放锁：GET 比对与 DEL 必须在同一段 Lua 中完成，
     * 否则锁在两步之间过期时，可能删掉别的实例刚拿到的锁
     */
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
        "if redis.call('get', KEYS[1]) == ARGV[1] then "
            + "return redis.call('del', KEYS[1]) else return 0 end",
        Long.class);

    /**
     * 每小时执行一次，将待同步文章的浏览量、点赞数回写 MySQL
     */
    @Scheduled(cron = "0 0 * * * *")
    public void syncStatsToDb() {
        String lockKey = RedisKeyConstant.VIEW_SYNC_LOCK;
        String lockValue = UUID.randomUUID().toString();

        if (!tryLock(lockKey, lockValue, LOCK_TIMEOUT)) {
            log.info("统计同步：其他实例正在执行，跳过");
            return;
        }

        List<Long> dirtyIds = new ArrayList<>();
        try {
            drainDirtyIds(dirtyIds);
            if (dirtyIds.isEmpty()) {
                log.info("统计同步：没有待同步的文章");
                return;
            }
            syncDirtyArticles(dirtyIds);
        } catch (Exception e) {
            // 已从脏集合弹出的文章重新入队；重复同步是幂等的，不会把计数写错
            log.error("统计同步任务执行失败, 待同步数量={}, 将重新入队", dirtyIds.size(), e);
            remarkDirty(dirtyIds);
        } finally {
            releaseLock(lockKey, lockValue);
        }
    }

    /**
     * 用 SPOP 取出待同步文章
     *
     * <p>SPOP 是原子的：取出即移除。同步期间产生的新增量会由写入方重新加入，
     * 下一轮同步最新值，不会丢更新。
     */
    private void drainDirtyIds(List<Long> ids) {
        while (ids.size() < MAX_ARTICLES_PER_RUN) {
            String value = redisUtil.sPop(RedisKeyConstant.ARTICLE_STATS_DIRTY);
            if (value == null) {
                break;
            }
            try {
                ids.add(Long.parseLong(value));
            } catch (NumberFormatException ignored) {
                // 脏集合里的非法值直接丢弃
            }
        }
    }

    /**
     * 读取 Redis 实时值并回写数据库
     */
    private void syncDirtyArticles(List<Long> dirtyIds) {
        Map<Long, Article> statsMap = loadStats(dirtyIds);

        List<Long> viewIds = new ArrayList<>();
        List<Long> viewValues = new ArrayList<>();
        List<Long> likeIds = new ArrayList<>();
        List<Long> likeValues = new ArrayList<>();
        int viewSkipped = 0;
        int likeSkipped = 0;

        for (Long articleId : dirtyIds) {
            Article stats = statsMap.get(articleId);
            if (stats == null) {
                // 文章已删除，脏标记随本次消费丢弃
                continue;
            }
            try {
                String viewKey = RedisKeyConstant.articleView(articleId);
                String cachedView = redisUtil.get(viewKey);
                if (cachedView == null) {
                    viewSkipped++;
                } else {
                    long redisView = Long.parseLong(cachedView);
                    long dbView = stats.getViewCount() == null ? 0L : stats.getViewCount();
                    if (redisView < dbView) {
                        // Redis 丢数据或被淘汰：用数据库值回填，绝不反向覆盖数据库
                        redisUtil.set(viewKey, String.valueOf(dbView));
                        viewSkipped++;
                    } else {
                        viewIds.add(articleId);
                        viewValues.add(redisView);
                    }
                }

                // 点赞数以 article_like 关系表为正本，按 COUNT(*) 校准，
                // 不再依赖 Redis 缓存；缓存丢失不会再把数据库计数改小
                int dbLikeCount = articleLikeMapper.countByArticle(articleId);
                if (dbLikeCount > 0) {
                    likeIds.add(articleId);
                    likeValues.add((long) dbLikeCount);
                } else {
                    // 明细为空时无法区分「全部取消」和「升级前无法回填的历史计数」，
                    // 不覆盖数据库里可能存在的历史值
                    likeSkipped++;
                }
            } catch (Exception e) {
                log.error("读取待同步统计失败, articleId={}, 将重新入队", articleId, e);
                remarkDirty(Collections.singletonList(articleId));
            }
        }

        batchUpdateView(viewIds, viewValues);
        batchUpdateLike(likeIds, likeValues);

        log.info("统计同步完成, 待同步 {} 篇, 更新浏览量 {} 条, 更新点赞数 {} 条, "
                + "跳过或回填浏览量 {} 条, 跳过点赞数 {} 条",
            dirtyIds.size(), viewIds.size(), likeIds.size(), viewSkipped, likeSkipped);
    }

    /**
     * 分片查询数据库计数，用作浏览量防倒退的基准
     */
    private Map<Long, Article> loadStats(List<Long> articleIds) {
        Map<Long, Article> statsMap = new HashMap<>();
        for (int start = 0; start < articleIds.size(); start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, articleIds.size());
            List<Article> batch = articleMapper.findStatsByIds(articleIds.subList(start, end));
            if (batch == null) {
                continue;
            }
            for (Article article : batch) {
                statsMap.put(article.getId(), article);
            }
        }
        return statsMap;
    }

    private void batchUpdateView(List<Long> ids, List<Long> values) {
        for (int start = 0; start < ids.size(); start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, ids.size());
            List<Long> idBatch = ids.subList(start, end);
            List<Long> valueBatch = values.subList(start, end);
            try {
                articleMapper.batchUpdateViewCount(idBatch, valueBatch);
            } catch (Exception e) {
                log.error("批量同步浏览量失败, 降级为逐条更新, 数量={}", idBatch.size(), e);
                fallbackUpdateViewCount(idBatch, valueBatch);
            }
        }
    }

    private void batchUpdateLike(List<Long> ids, List<Long> values) {
        for (int start = 0; start < ids.size(); start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, ids.size());
            List<Long> idBatch = ids.subList(start, end);
            List<Long> valueBatch = values.subList(start, end);
            try {
                articleMapper.batchUpdateLikeCount(idBatch, valueBatch);
            } catch (Exception e) {
                log.error("批量同步点赞数失败, 降级为逐条更新, 数量={}", idBatch.size(), e);
                fallbackUpdateLikeCount(idBatch, valueBatch);
            }
        }
    }

    /**
     * 浏览量降级逐条更新，仍失败的文章重新入队
     */
    private void fallbackUpdateViewCount(List<Long> ids, List<Long> values) {
        int failed = 0;
        for (int i = 0; i < ids.size(); i++) {
            try {
                articleMapper.updateViewCount(ids.get(i), values.get(i));
            } catch (Exception e) {
                failed++;
                log.error("逐条同步浏览量失败, articleId={}, 将重新入队", ids.get(i), e);
                remarkDirty(Collections.singletonList(ids.get(i)));
            }
        }
        log.info("浏览量降级逐条同步完成, 成功 {} 条, 失败 {} 条", ids.size() - failed, failed);
    }

    /**
     * 点赞数降级逐条更新，仍失败的文章重新入队
     */
    private void fallbackUpdateLikeCount(List<Long> ids, List<Long> values) {
        int failed = 0;
        for (int i = 0; i < ids.size(); i++) {
            try {
                articleMapper.updateLikeCount(ids.get(i), values.get(i));
            } catch (Exception e) {
                failed++;
                log.error("逐条同步点赞数失败, articleId={}, 将重新入队", ids.get(i), e);
                remarkDirty(Collections.singletonList(ids.get(i)));
            }
        }
        log.info("点赞数降级逐条同步完成, 成功 {} 条, 失败 {} 条", ids.size() - failed, failed);
    }

    /**
     * 把文章重新加入待同步集合
     */
    private void remarkDirty(List<Long> articleIds) {
        for (Long articleId : articleIds) {
            try {
                redisUtil.sAdd(RedisKeyConstant.ARTICLE_STATS_DIRTY, String.valueOf(articleId));
            } catch (Exception e) {
                log.warn("重新标记待同步文章失败, articleId={}", articleId, e);
            }
        }
    }

    /**
     * 尝试获取 Redis 分布式锁
     */
    private boolean tryLock(String key, String value, Duration timeout) {
        try {
            Boolean result = redisUtil.getRedisTemplate().opsForValue().setIfAbsent(key, value, timeout);
            return result != null && result;
        } catch (Exception e) {
            log.warn("获取分布式锁失败, key={}", key, e);
            return false;
        }
    }

    /**
     * 原子释放 Redis 分布式锁
     */
    private void releaseLock(String key, String value) {
        try {
            redisUtil.getRedisTemplate().execute(RELEASE_LOCK_SCRIPT,
                Collections.singletonList(key), value);
        } catch (Exception e) {
            log.warn("释放分布式锁失败, key={}", key, e);
        }
    }
}
