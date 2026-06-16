package com.novablog.rag.service;

import com.novablog.config.AiProperties;
import com.novablog.entity.Article;
import com.novablog.rag.entity.ArticleChunk;
import com.novablog.rag.util.MarkdownUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 文章分块服务
 * 将 Markdown 文章切分为适合 Embedding 的文本片段
 */
@Service
@RequiredArgsConstructor
public class ChunkingService {

    private final AiProperties aiProperties;

    /**
     * 将文章切分为向量片段
     *
     * @param article 文章实体
     * @return 片段列表（尚未填充 embedding）
     */
    public List<ArticleChunk> split(Article article) {
        List<ArticleChunk> chunks = new ArrayList<>();
        if (article == null || article.getContent() == null || article.getContent().isEmpty()) {
            return chunks;
        }

        String title = article.getTitle() == null ? "" : article.getTitle();
        String summary = article.getSummary() == null ? "" : article.getSummary();
        String content = article.getContent();

        int chunkSize = aiProperties.getRag().getChunkSize();
        int chunkOverlap = aiProperties.getRag().getChunkOverlap();

        // 先按 Markdown 标题把文章分成大段
        List<String> sections = MarkdownUtils.splitByHeadings(content);
        if (sections.isEmpty()) {
            sections.add(content);
        }

        int chunkIndex = 0;
        for (String section : sections) {
            String plainSection = MarkdownUtils.toPlainText(section);
            if (plainSection.isEmpty()) {
                continue;
            }

            // 每个大段前追加标题和摘要，增强语义
            String enrichedPrefix = buildEnrichedPrefix(title, summary);
            String sectionText = enrichedPrefix + plainSection;

            // 如果段落较短，直接作为一个 chunk
            if (MarkdownUtils.estimateTokens(sectionText) <= chunkSize) {
                chunks.add(buildChunk(article.getId(), chunkIndex++, sectionText));
                continue;
            }

            // 长段落再按 token 切分
            List<String> subChunks = MarkdownUtils.splitByTokens(plainSection, chunkSize, chunkOverlap);
            for (String subChunk : subChunks) {
                if (subChunk.isEmpty()) {
                    continue;
                }
                // 每个子 chunk 都携带标题和摘要前缀
                String enrichedChunk = enrichedPrefix + subChunk;
                chunks.add(buildChunk(article.getId(), chunkIndex++, enrichedChunk));
            }
        }

        return chunks;
    }

    /**
     * 构造增强前缀（标题 + 摘要）
     */
    private String buildEnrichedPrefix(String title, String summary) {
        StringBuilder prefix = new StringBuilder();
        if (!title.isEmpty()) {
            prefix.append("文章标题：").append(title).append("\n");
        }
        if (!summary.isEmpty()) {
            prefix.append("文章摘要：").append(summary).append("\n");
        }
        if (prefix.length() > 0) {
            prefix.append("正文内容：");
        }
        return prefix.toString();
    }

    /**
     * 构造 ArticleChunk
     */
    private ArticleChunk buildChunk(Long articleId, int chunkIndex, String content) {
        ArticleChunk chunk = new ArticleChunk();
        chunk.setArticleId(articleId);
        chunk.setChunkIndex(chunkIndex);
        chunk.setContent(content);
        chunk.setTokenCount(MarkdownUtils.estimateTokens(content));
        return chunk;
    }
}
