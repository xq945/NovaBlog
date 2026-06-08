package com.novablog.controller;

import com.novablog.common.Result;
import com.novablog.common.UserContext;
import com.novablog.common.exception.BusinessException;
import com.novablog.dto.CategoryDTO;
import com.novablog.entity.Category;
import com.novablog.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 分类控制器
 */
@RestController
@RequestMapping("/category")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * 查询所有分类
     *
     * @return 分类列表
     */
    @GetMapping("/list")
    public Result<List<Category>> list() {
        return Result.success(categoryService.findAll());
    }

    /**
     * 创建分类
     *
     * @param dto 分类参数
     * @return 分类ID
     */
    @PostMapping
    public Result<Long> create(@RequestBody CategoryDTO dto) {
        checkAdmin();
        if (dto.getName() == null || dto.getName().isEmpty() || dto.getName().length() > 20) {
            throw new BusinessException("分类名称长度必须为1-20位");
        }
        Long id = categoryService.create(dto.getName(), dto.getDescription());
        return Result.success(id);
    }

    /**
     * 修改分类
     *
     * @param dto 分类参数
     * @return 成功结果
     */
    @PutMapping
    public Result<Void> update(@RequestBody CategoryDTO dto) {
        checkAdmin();
        if (dto.getId() == null) {
            throw new BusinessException("分类ID不能为空");
        }
        if (dto.getName() == null || dto.getName().isEmpty() || dto.getName().length() > 20) {
            throw new BusinessException("分类名称长度必须为1-20位");
        }
        categoryService.update(dto.getId(), dto.getName(), dto.getDescription());
        return Result.success();
    }

    /**
     * 删除分类
     *
     * @param id 分类ID
     * @return 成功结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        checkAdmin();
        categoryService.delete(id);
        return Result.success();
    }

    private void checkAdmin() {
        if (!"ADMIN".equals(UserContext.getRole())) {
            throw new BusinessException(403, "无权访问");
        }
    }
}
