package com.novablog.controller;

import com.novablog.common.Result;
import com.novablog.entity.Tag;
import com.novablog.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
