package com.novablog.rag.mapper;

import com.novablog.rag.entity.ArticleChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文章向量分片数据访问层
 */
@Mapper
public interface ArticleChunkMapper {

    /**
     * 批量插入片段
     *
     * @param chunks 片段列表
     * @return 影响行数
     */
    int batchInsert(@Param("chunks") List<ArticleChunk> chunks);

    /**
     * 根据文章ID删除所有片段
     *
     * @param articleId 文章ID
     * @return 影响行数
     */
    int deleteByArticleId(@Param("articleId") Long articleId);

    /**
     * 查询所有已发布文章的向量分片
     *
     * @return 分片列表
     */
    List<ArticleChunk> findAllPublishedChunks();

    /**
     * 根据文章ID列表查询分片
     *
     * @param articleIds 文章ID列表
     * @return 分片列表
     */
    List<ArticleChunk> findByArticleIds(@Param("articleIds") List<Long> articleIds);
}
