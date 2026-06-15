package com.novablog.task;

import com.novablog.common.constant.RedisKeyConstant;
import com.novablog.mapper.ArticleMapper;
import com.novablog.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 文章统计数据同步定时任务
 * 每小时将 Redis 中的浏览量、点赞数同步回 MySQL，保证持久化
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ViewCountSyncTask {

    private final RedisUtil redisUtil;
    private final ArticleMapper articleMapper;

    /**
     * 分布式锁超时时间：10分钟
     */
    private static final Duration LOCK_TIMEOUT = Duration.ofMinutes(10);

    /**
     * 每小时执行一次，将 Redis 浏览量、点赞数同步到 MySQL
     * 使用 Redis 分布式锁防止多实例重复执行
     */
    @Scheduled(cron = "0 0 * * * *")
    public void syncStatsToDb() {
        String lockKey = RedisKeyConstant.VIEW_SYNC_LOCK;
        String lockValue = UUID.randomUUID().toString();

        // 1. 尝试获取分布式锁
        boolean locked = tryLock(lockKey, lockValue, LOCK_TIMEOUT);
        if (!locked) {
            log.info("统计同步：其他实例正在执行，跳过");
            return;
        }

        try {
            syncViewCount();
            syncLikeCount();
        } finally {
            // 释放锁（仅释放自己持有的锁）
            releaseLock(lockKey, lockValue);
        }
    }

    /**
     * 同步浏览量
     */
    private void syncViewCount() {
        Set<String> keys = redisUtil.scan(RedisKeyConstant.ARTICLE_VIEW + "*");
        if (keys == null || keys.isEmpty()) {
            log.info("浏览量同步：Redis 中没有数据，跳过");
            return;
        }

        List<Long> articleIdList = new ArrayList<>();
        List<Long> viewCountList = new ArrayList<>();
        int skipCount = 0;

        for (String key : keys) {
            try {
                String articleIdStr = key.substring(key.lastIndexOf(":") + 1);
                Long articleId = Long.parseLong(articleIdStr);
                String viewCountStr = redisUtil.get(key);
                if (viewCountStr != null) {
                    articleIdList.add(articleId);
                    viewCountList.add(Long.parseLong(viewCountStr));
                } else {
                    skipCount++;
                }
            } catch (Exception e) {
                skipCount++;
                log.error("读取 Redis 浏览量失败, key={}", key, e);
            }
        }

        if (articleIdList.isEmpty()) {
            log.info("浏览量同步：无有效数据需要同步，跳过 {} 条", skipCount);
            return;
        }

        try {
            int updated = articleMapper.batchUpdateViewCount(articleIdList, viewCountList);
            log.info("浏览量同步完成, 批量更新 {} 条, 跳过 {} 条", updated, skipCount);
        } catch (Exception e) {
            log.error("批量同步浏览量失败, 数据量={}", articleIdList.size(), e);
            fallbackUpdateViewCount(articleIdList, viewCountList);
        }
    }

    /**
     * 同步点赞数
     */
    private void syncLikeCount() {
        Set<String> keys = redisUtil.scan(RedisKeyConstant.ARTICLE_LIKE + "*");
        if (keys == null || keys.isEmpty()) {
            log.info("点赞数同步：Redis 中没有数据，跳过");
            return;
        }

        List<Long> articleIdList = new ArrayList<>();
        List<Long> likeCountList = new ArrayList<>();
        int skipCount = 0;

        for (String key : keys) {
            try {
                String articleIdStr = key.substring(key.lastIndexOf(":") + 1);
                Long articleId = Long.parseLong(articleIdStr);
                Long likeCount = redisUtil.sCard(key);
                if (likeCount != null) {
                    articleIdList.add(articleId);
                    likeCountList.add(likeCount);
                } else {
                    skipCount++;
                }
            } catch (Exception e) {
                skipCount++;
                log.error("读取 Redis 点赞数失败, key={}", key, e);
            }
        }

        if (articleIdList.isEmpty()) {
            log.info("点赞数同步：无有效数据需要同步，跳过 {} 条", skipCount);
            return;
        }

        try {
            int updated = articleMapper.batchUpdateLikeCount(articleIdList, likeCountList);
            log.info("点赞数同步完成, 批量更新 {} 条, 跳过 {} 条", updated, skipCount);
        } catch (Exception e) {
            log.error("批量同步点赞数失败, 数据量={}", articleIdList.size(), e);
            fallbackUpdateLikeCount(articleIdList, likeCountList);
        }
    }

    /**
     * 浏览量降级逐条更新
     */
    private void fallbackUpdateViewCount(List<Long> articleIdList, List<Long> viewCountList) {
        int successCount = 0;
        int failCount = 0;
        for (int i = 0; i < articleIdList.size(); i++) {
            try {
                articleMapper.updateViewCount(articleIdList.get(i), viewCountList.get(i));
                successCount++;
            } catch (Exception ex) {
                failCount++;
                log.error("逐条同步浏览量失败, articleId={}", articleIdList.get(i), ex);
            }
        }
        log.info("浏览量降级逐条同步完成, 成功 {} 条, 失败 {} 条", successCount, failCount);
    }

    /**
     * 点赞数降级逐条更新
     */
    private void fallbackUpdateLikeCount(List<Long> articleIdList, List<Long> likeCountList) {
        int successCount = 0;
        int failCount = 0;
        for (int i = 0; i < articleIdList.size(); i++) {
            try {
                articleMapper.updateLikeCount(articleIdList.get(i), likeCountList.get(i));
                successCount++;
            } catch (Exception ex) {
                failCount++;
                log.error("逐条同步点赞数失败, articleId={}", articleIdList.get(i), ex);
            }
        }
        log.info("点赞数降级逐条同步完成, 成功 {} 条, 失败 {} 条", successCount, failCount);
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
     * 释放 Redis 分布式锁
     */
    private void releaseLock(String key, String value) {
        try {
            String currentValue = redisUtil.get(key);
            if (value.equals(currentValue)) {
                redisUtil.del(key);
            }
        } catch (Exception e) {
            log.warn("释放分布式锁失败, key={}", key, e);
        }
    }
}
