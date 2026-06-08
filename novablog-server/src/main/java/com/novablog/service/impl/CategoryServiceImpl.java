package com.novablog.service.impl;

import com.novablog.common.exception.BusinessException;
import com.novablog.entity.Category;
import com.novablog.mapper.CategoryMapper;
import com.novablog.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 分类业务层实现
 */
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;

    @Override
    public List<Category> findAll() {
        return categoryMapper.findAll();
    }

    @Override
    public Long create(String name, String description) {
        Category category = new Category();
        category.setName(name);
        category.setDescription(description);
        categoryMapper.insert(category);
        return category.getId();
    }

    @Override
    public void update(Long id, String name, String description) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setDescription(description);
        categoryMapper.update(category);
    }

    @Override
    public void delete(Long id) {
        Long count = categoryMapper.countArticlesByCategoryId(id);
        if (count != null && count > 0) {
            throw new BusinessException("该分类下有文章，无法删除");
        }
        categoryMapper.deleteById(id);
    }
}
