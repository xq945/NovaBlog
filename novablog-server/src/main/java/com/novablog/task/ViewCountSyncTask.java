package com.novablog.task;

import com.novablog.mapper.ArticleMapper;
import com.novablog.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
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

        // 2. 收集所有 articleId 和 viewCount
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

        // 3. 批量更新 MySQL（CASE WHEN 批量更新，效率远高于逐条 UPDATE）
        try {
            int updated = articleMapper.batchUpdateViewCount(articleIdList, viewCountList);
            log.info("浏览量同步完成, 批量更新 {} 条, 跳过 {} 条", updated, skipCount);
        } catch (Exception e) {
            log.error("批量同步浏览量失败, 数据量={}", articleIdList.size(), e);
            // 降级：逐条更新
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
            log.info("降级逐条同步完成, 成功 {} 条, 失败 {} 条", successCount, failCount);
        }
    }
}
