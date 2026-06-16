package com.novablog.task;

import com.novablog.rag.service.ArticleIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 文章向量索引同步任务
 * 定时重试未索引或索引失败的文章
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ArticleIndexSyncTask {

    private final ArticleIndexService articleIndexService;

    /**
     * 每 5 分钟检查一次未索引/索引失败的文章
     */
    @Scheduled(cron = "0 */5 * * * *")
    public void sync() {
        log.info("开始执行文章向量索引同步任务");
        try {
            articleIndexService.retryUnindexed();
        } catch (Exception e) {
            log.error("文章向量索引同步任务执行失败", e);
        }
        log.info("文章向量索引同步任务执行完成");
    }
}
