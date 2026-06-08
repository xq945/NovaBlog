package com.novablog.service;

import com.novablog.entity.Tag;

import java.util.List;

/**
 * 标签业务层接口
 */
public interface TagService {

    /**
     * 查询所有标签
     *
     * @return 标签列表
     */
    List<Tag> findAll();

    /**
     * 创建标签
     *
     * @param name 标签名称
     * @return 标签ID
     */
    Long create(String name);

    /**
     * 修改标签
     *
     * @param id   标签ID
     * @param name 标签名称
     */
    void update(Long id, String name);

    /**
     * 删除标签
     *
     * @param id 标签ID
     */
    void delete(Long id);
}
