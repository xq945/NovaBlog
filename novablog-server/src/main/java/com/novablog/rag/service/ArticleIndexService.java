package com.novablog.rag.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novablog.entity.Article;
import com.novablog.mapper.ArticleMapper;
import com.novablog.rag.dto.IndexStatusVO;
import com.novablog.rag.entity.ArticleChunk;
import com.novablog.rag.event.ArticlePublishedEvent;
import com.novablog.rag.mapper.ArticleChunkMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * 文章向量索引服务
 * 负责把文章切块、生成 embedding 并持久化到 MySQL
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleIndexService {

    private final ArticleMapper articleMapper;
    private final ArticleChunkMapper articleChunkMapper;
    private final ChunkingService chunkingService;
    private final EmbeddingModel embeddingModel;
    private final ObjectMapper objectMapper;

    /**
     * 监听文章发布/更新事件，事务提交后异步重建索引
     */
    @Async("ragTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onArticlePublished(ArticlePublishedEvent event) {
        Long articleId = event.getArticleId();
        log.info("收到文章发布事件, articleId={}, newlyCreated={}", articleId, event.isNewlyCreated());
        try {
            indexArticle(articleId);
        } catch (Exception e) {
            log.error("文章索引失败, articleId={}", articleId, e);
            markIndexed(articleId, 2);
        }
    }

    /**
     * 为单篇文章构建向量索引
     *
     * @param articleId 文章ID
     */
    @Transactional
    public void indexArticle(Long articleId) {
        Article article = articleMapper.findById(articleId);
        if (article == null) {
            log.warn("索引文章不存在, articleId={}", articleId);
            return;
        }

        // 只有已发布文章才建立索引
        if (article.getStatus() == null || article.getStatus() != 1) {
            log.info("文章非发布状态，跳过索引, articleId={}, status={}", articleId, article.getStatus());
            articleChunkMapper.deleteByArticleId(articleId);
            markIndexed(articleId, 0);
            return;
        }

        // 1. 切分
        List<ArticleChunk> chunks = chunkingService.split(article);
        if (chunks.isEmpty()) {
            log.info("文章无有效内容可索引, articleId={}", articleId);
            markIndexed(articleId, 1);
            return;
        }

        // 2. 生成 embedding
        List<String> texts = new ArrayList<>();
        for (ArticleChunk chunk : chunks) {
            texts.add(chunk.getContent());
        }

        List<float[]> embeddings = embeddingModel.embed(texts);
        if (embeddings == null || embeddings.size() != chunks.size()) {
            throw new IllegalStateException("Embedding 数量与 chunk 数量不一致: "
                    + (embeddings == null ? 0 : embeddings.size()) + " != " + chunks.size());
        }

        for (int i = 0; i < chunks.size(); i++) {
            chunks.get(i).setEmbedding(toJson(embeddings.get(i)));
        }

        // 3. 先删除旧 chunk，再写入新 chunk
        articleChunkMapper.deleteByArticleId(articleId);
        articleChunkMapper.batchInsert(chunks);

        // 4. 标记索引成功
        markIndexed(articleId, 1);
        log.info("文章索引成功, articleId={}, chunks={}", articleId, chunks.size());
    }

    /**
     * 删除文章索引
     *
     * @param articleId 文章ID
     */
    @Transactional
    public void deleteIndex(Long articleId) {
        articleChunkMapper.deleteByArticleId(articleId);
        log.info("删除文章索引, articleId={}", articleId);
    }

    /**
     * 重建所有已发布文章的索引
     * 供管理员手动触发
     */
    @Transactional
    public void rebuildAll() {
        List<Article> articles = articleMapper.findAllPublished();
        for (Article article : articles) {
            try {
                indexArticle(article.getId());
            } catch (Exception e) {
                log.error("重建索引失败, articleId={}", article.getId(), e);
                markIndexed(article.getId(), 2);
            }
        }
    }

    /**
     * 重试索引失败或未索引的文章
     */
    @Transactional
    public void retryUnindexed() {
        List<Article> articles = articleMapper.findByIndexedStatus(0);
        articles.addAll(articleMapper.findByIndexedStatus(2));
        for (Article article : articles) {
            try {
                indexArticle(article.getId());
            } catch (Exception e) {
                log.error("重试索引失败, articleId={}", article.getId(), e);
                markIndexed(article.getId(), 2);
            }
        }
    }

    /**
     * 获取索引状态统计
     */
    public IndexStatusVO getIndexStatus() {
        Long indexed = articleMapper.countByIndexedStatus(1);
        Long unindexed = articleMapper.countByIndexedStatus(0);
        Long failed = articleMapper.countByIndexedStatus(2);
        return new IndexStatusVO(indexed, unindexed, failed);
    }

    /**
     * 更新文章索引状态
     */
    private void markIndexed(Long articleId, int status) {
        Article article = new Article();
        article.setId(articleId);
        article.setIndexed(status);
        articleMapper.update(article);
    }

    /**
     * 将 float[] 序列化为 JSON 字符串
     */
    private String toJson(float[] vector) {
        List<Double> list = new ArrayList<>(vector.length);
        for (float value : vector) {
            list.add((double) value);
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("向量序列化失败", e);
        }
    }
}
