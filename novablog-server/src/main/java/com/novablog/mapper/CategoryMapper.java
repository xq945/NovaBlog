package com.novablog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novablog.entity.Category;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CategoryMapper extends BaseMapper<Category> {

    List<Category> findAll();

    Category findById(Long id);

    Category findByName(@Param("name") String name);

    int update(Category category);

    Long countArticlesByCategoryId(@Param("categoryId") Long categoryId);
}
