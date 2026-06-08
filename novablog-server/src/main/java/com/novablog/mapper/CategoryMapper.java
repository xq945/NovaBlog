package com.novablog.mapper;

import com.novablog.entity.Category;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 分类数据访问层
 */
@Mapper
public interface CategoryMapper {

    /**
     * 查询所有分类
     *
     * @return 分类列表
     */
    List<Category> findAll();

    /**
     * 根据ID查询分类
     *
     * @param id 分类ID
     * @return 分类实体
     */
    Category findById(Long id);

    /**
     * 根据名称查询分类
     *
     * @param name 分类名称
     * @return 分类实体
     */
    Category findByName(@Param("name") String name);

    /**
     * 插入分类
     *
     * @param category 分类实体
     * @return 影响行数
     */
    int insert(Category category);

    /**
     * 更新分类
     *
     * @param category 分类实体
     * @return 影响行数
     */
    int update(Category category);

    /**
     * 删除分类
     *
     * @param id 分类ID
     * @return 影响行数
     */
    int deleteById(@Param("id") Long id);

    /**
     * 查询引用该分类的文章数量
     *
     * @param categoryId 分类ID
     * @return 文章数量
     */
    Long countArticlesByCategoryId(@Param("categoryId") Long categoryId);
}
