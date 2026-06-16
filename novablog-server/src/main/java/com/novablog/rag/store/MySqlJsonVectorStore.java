package com.novablog.rag.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novablog.rag.entity.ArticleChunk;
import com.novablog.rag.mapper.ArticleChunkMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 MySQL JSON 向量字段的 VectorStore 实现
 * 复用现有 article_chunk 表，为 Spring AI 提供检索能力
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MySqlJsonVectorStore implements VectorStore {

    private final EmbeddingModel embeddingModel;
    private final ArticleChunkMapper articleChunkMapper;
    private final ObjectMapper objectMapper;

    /**
     * 相似度检索
     */
    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        String query = request.getQuery();
        if (query == null || query.trim().isEmpty()) {
            return List.of();
        }

        // 1. 问题向量化
        float[] queryVectorArray = embeddingModel.embed(query.trim());
        List<Double> queryVector = toDoubleList(queryVectorArray);

        // 2. 加载所有已发布文章的 chunk
        List<ArticleChunk> allChunks = articleChunkMapper.findAllPublishedChunks();
        if (allChunks.isEmpty()) {
            return List.of();
        }

        // 3. 计算相似度并排序
        double threshold = request.getSimilarityThreshold();
        List<ScoredChunk> scoredChunks = allChunks.stream()
                .map(chunk -> {
                    List<Double> vector = parseJson(chunk.getEmbedding());
                    if (vector.isEmpty()) {
                        return null;
                    }
                    double score = cosineSimilarity(queryVector, vector);
                    return new ScoredChunk(chunk, score);
                })
                .filter(s -> s != null && s.score >= threshold)
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                .limit(request.getTopK())
                .toList();

        // 4. 转换为 Spring AI Document
        List<Document> results = new ArrayList<>(scoredChunks.size());
        for (ScoredChunk scored : scoredChunks) {
            ArticleChunk chunk = scored.chunk();
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("articleId", chunk.getArticleId());
            metadata.put("chunkIndex", chunk.getChunkIndex());
            metadata.put("score", scored.score());
            results.add(Document.builder()
                    .id(chunk.getId().toString())
                    .text(chunk.getContent())
                    .metadata(metadata)
                    .build());
        }
        return results;
    }

    /**
     * 暂不实现批量写入，索引逻辑仍由 ArticleIndexService 直接操作 article_chunk 表
     */
    @Override
    public void add(List<Document> documents) {
        throw new UnsupportedOperationException("MySqlJsonVectorStore 暂不支持 add，请使用 ArticleIndexService 建立索引");
    }

    /**
     * 暂不实现删除，由 ArticleIndexService 直接操作 article_chunk 表
     */
    @Override
    public void delete(List<String> idList) {
        throw new UnsupportedOperationException("MySqlJsonVectorStore 暂不支持 delete，请使用 ArticleIndexService 删除索引");
    }

    /**
     * 暂不实现表达式删除
     */
    @Override
    public void delete(Filter.Expression filterExpression) {
        throw new UnsupportedOperationException("MySqlJsonVectorStore 暂不支持 delete");
    }

    /**
     * float[] 转为 List<Double>
     */
    private List<Double> toDoubleList(float[] array) {
        List<Double> list = new ArrayList<>(array.length);
        for (float value : array) {
            list.add((double) value);
        }
        return list;
    }

    /**
     * 将 JSON 字符串解析为 Double 列表
     */
    private List<Double> parseJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<Double>>() {
            });
        } catch (Exception e) {
            log.error("向量 JSON 解析失败: {}", json, e);
            return List.of();
        }
    }

    /**
     * 计算两个向量的余弦相似度
     */
    private double cosineSimilarity(List<Double> a, List<Double> b) {
        if (a == null || b == null || a.size() != b.size() || a.isEmpty()) {
            return -1.0;
        }

        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.size(); i++) {
            double ai = a.get(i);
            double bi = b.get(i);
            dot += ai * bi;
            normA += ai * ai;
            normB += bi * bi;
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * 带分数的 chunk 内部记录
     */
    private record ScoredChunk(ArticleChunk chunk, double score) {
    }
}
