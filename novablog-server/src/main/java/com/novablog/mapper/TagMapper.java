package com.novablog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novablog.dto.ArticleTagDTO;
import com.novablog.entity.Tag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TagMapper extends BaseMapper<Tag> {

    List<Tag> findAll();

    Tag findById(Long id);

    Tag findByName(@Param("name") String name);

    List<Tag> findByArticleId(@Param("articleId") Long articleId);

    int update(Tag tag);

    int deleteArticleTagByTagId(@Param("tagId") Long tagId);

    List<ArticleTagDTO> findRelationsByArticleIds(@Param("articleIds") List<Long> articleIds);
}
