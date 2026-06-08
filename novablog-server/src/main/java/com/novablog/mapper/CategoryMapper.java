package com.novablog.mapper;

import com.novablog.entity.Category;
import org.apache.ibatis.annotations.Mapper;

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
}
