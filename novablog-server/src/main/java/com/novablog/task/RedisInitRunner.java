package com.novablog.task;

import com.novablog.common.constant.RedisKeyConstant;
import com.novablog.entity.Article;
import com.novablog.mapper.ArticleLikeMapper;
import com.novablog.mapper.ArticleMapper;
import com.novablog.service.component.ArticleStatsComponent;
import com.novablog.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * 应用启动初始化
 *
 * <p>Redis 重启或数据被淘汰后，浏览量与热榜可能各缺一部分。这里逐篇比对：
 * 缺失的浏览量从 MySQL 回填，缺失的热榜成员按「浏览量 + 3 × 点赞数」补入。
 * 已存在的键与 ZSet 成员保持不动，避免用滞后的数据库值覆盖实时数据。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedisInitRunner implements ApplicationRunner {

    private final RedisUtil redisUtil;
    private final ArticleMapper articleMapper;
    private final ArticleLikeMapper articleLikeMapper;
    private final ArticleStatsComponent articleStatsComponent;

    @Override
    public void run(ApplicationArguments args) {
        // 1. 加载全部已发布文章
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

        // 2. 逐篇补齐浏览量 key 与热榜成员
        int viewRepaired = 0;
        int hotRepaired = 0;
        for (Article article : articles) {
            Long articleId = article.getId();
            long dbViewCount = article.getViewCount() != null ? article.getViewCount() : 0L;
            long dbLikeCount = article.getLikeCount() != null ? article.getLikeCount() : 0L;
            String viewKey = RedisKeyConstant.articleView(articleId);

            // 存量数据的统计 key 没有脏标记，启动时统一补一轮，
            // 保证首个同步周期能把升级前的增量对齐
            articleStatsComponent.markDirty(articleId);

            // 只补缺失或损坏的 key，已有的实时值不覆盖
            long viewCount;
            try {
                viewCount = parseViewCount(redisUtil.get(viewKey));
                if (viewCount < 0) {
                    viewCount = dbViewCount;
                    redisUtil.set(viewKey, String.valueOf(viewCount));
                    viewRepaired++;
                }
            } catch (Exception e) {
                log.error("初始化浏览量失败, articleId={}", articleId, e);
                continue;
            }

            // 只补缺失的 ZSet 成员，已有分数优先于数据库快照
            try {
                if (redisUtil.zScore(RedisKeyConstant.ARTICLE_HOT_ZSET,
                        String.valueOf(articleId)) == null) {
                    articleStatsComponent.updateHotScore(articleId, viewCount, dbLikeCount);
                    hotRepaired++;
                }
            } catch (Exception e) {
                log.error("初始化热门排行失败, articleId={}", articleId, e);
            }

            // 3. 点赞缓存：数据库是正本，缓存缺失时按关系表重建
            rebuildLikeCache(article);
        }

        log.info("Redis 初始化完成, 共 {} 篇已发布文章, 回填浏览量 {} 条, 补入热榜 {} 条",
            articles.size(), viewRepaired, hotRepaired);
    }

    /**
     * 重建点赞缓存
     *
     * <p>数据库是点赞关系的正本：正常情况下按关系表重建 Redis Set；
     * 首次升级时关系表为空而 Redis 里还有历史明细，则把 Redis 成员回填进表。
     */
    private void rebuildLikeCache(Article article) {
        Long articleId = article.getId();
        List<Long> dbMembers;
        try {
            dbMembers = articleLikeMapper.findUserIdsByArticle(articleId);
        } catch (Exception e) {
            log.error("读取点赞明细失败, articleId={}", articleId, e);
            return;
        }
        if (dbMembers == null) {
            dbMembers = new ArrayList<>();
        }

        List<Long> redisMembers = articleStatsComponent.getLikeMembers(articleId);
        if (dbMembers.isEmpty() && redisMembers != null && !redisMembers.isEmpty()) {
            // 升级回填：Redis 有明细、数据库还没有
            try {
                articleLikeMapper.batchInsertIgnore(articleId, redisMembers);
                dbMembers = redisMembers;
                log.info("回填点赞明细, articleId={}, count={}", articleId, dbMembers.size());
            } catch (Exception e) {
                log.error("回填点赞明细失败, articleId={}", articleId, e);
                return;
            }
        }

        if (dbMembers.isEmpty()) {
            // 关系表为空：可能确实没人点赞，也可能是升级前 Redis 已丢数据。
            // 保留数据库里可能存在的历史计数，不做校准
            return;
        }

        // 缓存与关系表一致时跳过重建，避免每次启动都 DEL + SADD 一遍
        if (redisMembers == null
                || !new HashSet<>(redisMembers).equals(new HashSet<>(dbMembers))) {
            articleStatsComponent.resetLikeCache(articleId, dbMembers);
        }
        syncLikeCountIfNeeded(article, dbMembers.size());
    }

    /**
     * 关系表是计数正本，数据库计数与明细数量不一致时校准
     */
    private void syncLikeCountIfNeeded(Article article, long actualCount) {
        long dbLikeCount = article.getLikeCount() != null ? article.getLikeCount() : 0L;
        if (dbLikeCount == actualCount) {
            return;
        }
        try {
            articleMapper.updateLikeCount(article.getId(), actualCount);
        } catch (Exception e) {
            log.warn("校准点赞数失败, articleId={}", article.getId(), e);
        }
    }

    /**
     * 解析 Redis 中的浏览量，缺失或格式非法返回 -1
     */
    private long parseViewCount(String value) {
        if (value == null || value.isBlank()) {
            return -1L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }
}
