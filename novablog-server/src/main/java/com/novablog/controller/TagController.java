package com.novablog.controller;

import com.novablog.common.Result;
import com.novablog.common.annotation.RequireAdmin;
import com.novablog.common.exception.BusinessException;
import com.novablog.dto.request.TagDTO;
import com.novablog.entity.Tag;
import com.novablog.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 标签控制器
 */
@RestController
@RequestMapping("/tag")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    /**
     * 查询所有标签
     *
     * @return 标签列表
     */
    @GetMapping("/list")
    public Result<List<Tag>> list() {
        return Result.success(tagService.findAll());
    }

    /**
     * 创建标签
     *
     * @param dto 标签参数
     * @return 标签ID
     */
    @PostMapping
    @RequireAdmin
    public Result<Long> create(@RequestBody TagDTO dto) {
        if (dto.getName() == null || dto.getName().isEmpty() || dto.getName().length() > 20) {
            throw new BusinessException("标签名称长度必须为1-20位");
        }
        Long id = tagService.create(dto.getName());
        return Result.success(id);
    }

    /**
     * 修改标签
     *
     * @param dto 标签参数
     * @return 成功结果
     */
    @PutMapping
    @RequireAdmin
    public Result<Void> update(@RequestBody TagDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("标签ID不能为空");
        }
        if (dto.getName() == null || dto.getName().isEmpty() || dto.getName().length() > 20) {
            throw new BusinessException("标签名称长度必须为1-20位");
        }
        tagService.update(dto.getId(), dto.getName());
        return Result.success();
    }

    /**
     * 删除标签
     *
     * @param id 标签ID
     * @return 成功结果
     */
    @DeleteMapping("/{id}")
    @RequireAdmin
    public Result<Void> delete(@PathVariable Long id) {
        tagService.delete(id);
        return Result.success();
    }
}
