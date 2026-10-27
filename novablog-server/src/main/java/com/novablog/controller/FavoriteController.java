package com.novablog.controller;

import com.novablog.common.PageResult;
import com.novablog.common.Result;
import com.novablog.service.FavoriteService;
import com.novablog.vo.ArticleVO;
import com.novablog.vo.FavoriteStatusVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "文章收藏", description = "收藏、取消收藏、我的收藏列表")
@RestController
@RequestMapping("/article/favorite")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @Operation(summary = "收藏文章", description = "重复收藏幂等，不会重复计数")
    @PostMapping("/{id}")
    public Result<FavoriteStatusVO> favorite(@PathVariable Long id) {
        return Result.success(favoriteService.favorite(id));
    }

    @Operation(summary = "取消收藏", description = "未收藏时调用同样成功")
    @DeleteMapping("/{id}")
    public Result<FavoriteStatusVO> unfavorite(@PathVariable Long id) {
        return Result.success(favoriteService.unfavorite(id));
    }

    @Operation(summary = "查询收藏状态")
    @GetMapping("/status")
    public Result<FavoriteStatusVO> status(@RequestParam Long articleId) {
        return Result.success(favoriteService.getStatus(articleId));
    }

    @Operation(summary = "我的收藏列表", description = "只展示仍已发布的文章，按收藏时间倒序")
    @GetMapping("/list")
    public Result<PageResult<ArticleVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(favoriteService.listMyFavorites(page, size));
    }
}
