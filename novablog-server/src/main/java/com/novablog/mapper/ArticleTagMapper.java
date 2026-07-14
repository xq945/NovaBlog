package com.novablog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novablog.entity.ArticleTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ArticleTagMapper extends BaseMapper<ArticleTag> {

    int batchInsert(@Param("articleId") Long articleId, @Param("tagIds") List<Long> tagIds);

    int deleteByArticleId(@Param("articleId") Long articleId);

    int deleteByArticleIdAndTagIds(@Param("articleId") Long articleId, @Param("tagIds") List<Long> tagIds);
}
