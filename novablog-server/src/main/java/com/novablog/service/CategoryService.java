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

    /**
     * 创建分类
     *
     * @param name        分类名称
     * @param description 分类描述
     * @return 分类ID
     */
    Long create(String name, String description);

    /**
     * 修改分类
     *
     * @param id          分类ID
     * @param name        分类名称
     * @param description 分类描述
     */
    void update(Long id, String name, String description);

    /**
     * 删除分类
     *
     * @param id 分类ID
     */
    void delete(Long id);
}
