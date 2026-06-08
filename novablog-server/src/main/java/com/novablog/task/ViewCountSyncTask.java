package com.novablog.task;

import com.novablog.mapper.ArticleMapper;
import com.novablog.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 浏览量同步定时任务
 * 每小时将 Redis 中的浏览量同步回 MySQL
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ViewCountSyncTask {

    private final RedisUtil redisUtil;
    private final ArticleMapper articleMapper;

    /**
     * Redis Key 前缀：浏览量
     */
    private static final String KEY_VIEW_PREFIX = "article:view:";

    /**
     * 每小时执行一次，将 Redis 浏览量同步到 MySQL
     */
    @Scheduled(cron = "0 0 * * * *")
    public void syncViewCountToDb() {
        // 1. 使用 SCAN 遍历所有 article:view:* key（避免 KEYS 阻塞）
        Set<String> keys = redisUtil.scan(KEY_VIEW_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            log.info("浏览量同步：Redis 中没有 article:view 数据，跳过");
            return;
        }

        // 2. 批量读取 Redis 值并更新 MySQL
        int successCount = 0;
        int failCount = 0;
        for (String key : keys) {
            try {
                String articleIdStr = key.substring(key.lastIndexOf(":") + 1);
                Long articleId = Long.parseLong(articleIdStr);
                String viewCountStr = redisUtil.get(key);
                if (viewCountStr != null) {
                    Long viewCount = Long.parseLong(viewCountStr);
                    articleMapper.updateViewCount(articleId, viewCount);
                    successCount++;
                }
            } catch (Exception e) {
                failCount++;
                log.error("同步浏览量失败, key={}", key, e);
            }
        }
        log.info("浏览量同步完成, 成功 {} 条, 失败 {} 条", successCount, failCount);
    }
}
