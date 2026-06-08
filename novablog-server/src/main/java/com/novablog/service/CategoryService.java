package com.novablog.service;

import com.novablog.entity.Category;

import java.util.List;

/**
 * 分类业务层接口
 */
public interface CategoryService {

    /**
     * 查询所有分类
     *
     * @return 分类列表
     */
    List<Category> findAll();
}
