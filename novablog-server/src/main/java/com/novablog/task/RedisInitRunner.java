package com.novablog.task;

import com.novablog.entity.Article;
import com.novablog.mapper.ArticleMapper;
import com.novablog.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 应用启动初始化
 * 若 Redis 中 article:view 数据为空，从 MySQL 加载
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedisInitRunner implements ApplicationRunner {

    private final RedisUtil redisUtil;
    private final ArticleMapper articleMapper;

    /**
     * Redis Key 前缀：浏览量
     */
    private static final String KEY_VIEW_PREFIX = "article:view:";

    @Override
    public void run(ApplicationArguments args) {
        // 1. 检查是否已有 article:view 数据
        Set<String> keys;
        try {
            keys = redisUtil.scan(KEY_VIEW_PREFIX + "*");
        } catch (Exception e) {
            log.warn("检查 Redis 浏览量数据失败", e);
            keys = null;
        }

        if (keys != null && !keys.isEmpty()) {
            log.info("Redis 中已有 {} 条浏览量数据, 跳过初始化", keys.size());
            return;
        }

        // 2. 从 MySQL 加载所有已发布文章的 view_count
        List<Article> articles;
        try {
            articles = articleMapper.findAllPublished();
        } catch (Exception e) {
            log.error("从 MySQL 加载文章列表失败", e);
            return;
        }

        if (articles == null || articles.isEmpty()) {
            log.info("MySQL 中没有已发布文章, 跳过初始化");
            return;
        }

        int successCount = 0;
        for (Article article : articles) {
            try {
                String key = KEY_VIEW_PREFIX + article.getId();
                Integer viewCount = article.getViewCount();
                redisUtil.set(key, String.valueOf(viewCount != null ? viewCount : 0));
                successCount++;
            } catch (Exception e) {
                log.error("初始化 Redis 浏览量失败, articleId={}", article.getId(), e);
            }
        }
        log.info("Redis 浏览量初始化完成, 共 {} 条, 成功 {} 条", articles.size(), successCount);
    }
}
