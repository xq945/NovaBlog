package com.novablog.controller;

import com.novablog.common.PageResult;
import com.novablog.common.Result;
import com.novablog.dto.ArticleDTO;
import com.novablog.service.ArticleService;
import com.novablog.vo.ArticleDetailVO;
import com.novablog.vo.ArticleVO;
import com.novablog.vo.HotArticleVO;
import com.novablog.vo.LikeStatusVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 文章控制器
 */
@RestController
@RequestMapping("/article")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;

    /**
     * 发布文章
     *
     * @param articleDTO 文章参数
     * @return 文章ID
     */
    @PostMapping
    public Result<Long> publish(@RequestBody ArticleDTO articleDTO) {
        Long articleId = articleService.publish(articleDTO);
        return Result.success(articleId);
    }

    /**
     * 查询文章列表（公开）
     *
     * @param page       页码
     * @param size       每页数量
     * @param categoryId 分类ID
     * @param keyword    关键词
     * @return 分页结果
     */
    @GetMapping("/list")
    public Result<PageResult<ArticleVO>> list(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword) {
        PageResult<ArticleVO> result = articleService.findList(page, size, categoryId, keyword);
        return Result.success(result);
    }

    /**
     * 查询文章详情
     *
     * @param id 文章ID
     * @return 文章详情
     */
    @GetMapping("/{id}")
    public Result<ArticleDetailVO> detail(@PathVariable Long id) {
        ArticleDetailVO detail = articleService.findDetail(id);
        return Result.success(detail);
    }

    /**
     * 修改文章
     *
     * @param articleDTO 文章参数
     * @return 成功结果
     */
    @PutMapping
    public Result<Void> update(@RequestBody ArticleDTO articleDTO) {
        articleService.update(articleDTO);
        return Result.success();
    }

    /**
     * 删除文章
     *
     * @param id 文章ID
     * @return 成功结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        articleService.delete(id);
        return Result.success();
    }

    /**
     * 点赞文章
     *
     * @param id 文章ID
     * @return 成功结果
     */
    @PostMapping("/like/{id}")
    public Result<Void> like(@PathVariable Long id) {
        articleService.like(id);
        return Result.success();
    }

    /**
     * 取消点赞
     *
     * @param id 文章ID
     * @return 成功结果
     */
    @DeleteMapping("/like/{id}")
    public Result<Void> unlike(@PathVariable Long id) {
        articleService.unlike(id);
        return Result.success();
    }

    /**
     * 查询点赞状态
     *
     * @param articleId 文章ID
     * @return 点赞状态
     */
    @GetMapping("/like/status")
    public Result<LikeStatusVO> likeStatus(@RequestParam Long articleId) {
        LikeStatusVO status = articleService.getLikeStatus(articleId);
        return Result.success(status);
    }

    /**
     * 热门文章列表（公开）
     *
     * @param size 数量
     * @return 热门文章列表
     */
    @GetMapping("/hot")
    public Result<List<HotArticleVO>> hot(
            @RequestParam(required = false, defaultValue = "10") Integer size) {
        List<HotArticleVO> list = articleService.findHotArticles(size);
        return Result.success(list);
    }
}
