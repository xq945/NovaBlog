package com.novablog.controller;

import com.novablog.common.Result;
import com.novablog.common.annotation.LogOperation;
import com.novablog.common.annotation.RequireAdmin;
import com.novablog.common.exception.BusinessException;
import com.novablog.dto.request.CategoryDTO;
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
    @RequireAdmin
    @LogOperation(target = "CATEGORY", operation = "CREATE")
    public Result<Long> create(@RequestBody CategoryDTO dto) {
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
    @RequireAdmin
    @LogOperation(target = "CATEGORY", operation = "UPDATE")
    public Result<Void> update(@RequestBody CategoryDTO dto) {
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
    @RequireAdmin
    @LogOperation(target = "CATEGORY", operation = "DELETE", detail = "删除分类ID：{0}")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.success();
    }
}
