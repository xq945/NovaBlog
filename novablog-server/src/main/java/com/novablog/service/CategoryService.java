package com.novablog.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.novablog.entity.Category;

import java.util.List;

public interface CategoryService extends IService<Category> {

    List<Category> findAll();

    Long create(String name, String description);

    void update(Long id, String name, String description);

    void delete(Long id);
}
