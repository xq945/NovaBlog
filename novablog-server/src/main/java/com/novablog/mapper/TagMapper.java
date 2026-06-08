package com.novablog.mapper;

import com.novablog.entity.Tag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 标签数据访问层
 */
@Mapper
public interface TagMapper {

    /**
     * 查询所有标签
     *
     * @return 标签列表
     */
    List<Tag> findAll();

    /**
     * 根据ID查询标签
     *
     * @param id 标签ID
     * @return 标签实体
     */
    Tag findById(Long id);

    /**
     * 根据名称查询标签
     *
     * @param name 标签名称
     * @return 标签实体
     */
    Tag findByName(@Param("name") String name);

    /**
     * 插入新标签
     *
     * @param tag 标签实体
     * @return 影响行数
     */
    int insert(Tag tag);

    /**
     * 根据文章ID查询标签列表
     *
     * @param articleId 文章ID
     * @return 标签列表
     */
    List<Tag> findByArticleId(@Param("articleId") Long articleId);
}
