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
}
