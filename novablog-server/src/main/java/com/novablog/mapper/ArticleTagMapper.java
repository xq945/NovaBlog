package com.novablog.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文章-标签关联数据访问层
 */
@Mapper
public interface ArticleTagMapper {

    /**
     * 插入文章-标签关联
     *
     * @param articleId 文章ID
     * @param tagId     标签ID
     * @return 影响行数
     */
    int insert(@Param("articleId") Long articleId, @Param("tagId") Long tagId);

    /**
     * 批量插入文章-标签关联
     *
     * @param articleId 文章ID
     * @param tagIds    标签ID列表
     * @return 影响行数
     */
    int batchInsert(@Param("articleId") Long articleId, @Param("tagIds") List<Long> tagIds);

    /**
     * 根据文章ID删除关联
     *
     * @param articleId 文章ID
     * @return 影响行数
     */
    int deleteByArticleId(@Param("articleId") Long articleId);
}
