package com.novablog.controller;

import com.novablog.common.PageResult;
import com.novablog.common.Result;
import com.novablog.common.annotation.RequireAdmin;
import com.novablog.dto.ArticleDTO;
import com.novablog.dto.ArticleImportResult;
import com.novablog.dto.SummaryRequest;
import com.novablog.service.AiSummaryService;
import com.novablog.service.ArticleImportService;
import com.novablog.service.ArticleService;
import com.novablog.vo.ArticleDetailVO;
import com.novablog.vo.ArticleVO;
import com.novablog.vo.HotArticleVO;
import com.novablog.vo.LikeStatusVO;
import com.novablog.vo.SummaryVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文章控制器
 */
@RestController
@RequestMapping("/article")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;
    private final ArticleImportService articleImportService;
    private final AiSummaryService aiSummaryService;

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
     * 导入文件生成文章草稿数据
     *
     * @param file 上传的文件（.md / .txt / .docx / .pdf）
     * @return 解析后的标题、正文、摘要
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ArticleImportResult> importFile(@RequestParam("file") MultipartFile file) {
        ArticleImportResult result = articleImportService.importFile(file);
        return Result.success(result);
    }

    /**
     * AI 自动生成文章摘要
     *
     * @param request 摘要请求
     * @return 生成的摘要
     */
    @PostMapping("/summary")
    public Result<SummaryVO> generateSummary(@Valid @RequestBody SummaryRequest request) {
        String summary = aiSummaryService.generateSummary(request.getContent());
        return Result.success(new SummaryVO(summary));
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

    /**
     * 管理员查询所有文章（含草稿）
     *
     * @param page    页码
     * @param size    每页数量
     * @param keyword 关键词
     * @return 文章分页结果
     */
    @GetMapping("/admin/list")
    @RequireAdmin
    public Result<PageResult<ArticleVO>> adminList(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword) {
        PageResult<ArticleVO> result = articleService.findAdminList(page, size, keyword);
        return Result.success(result);
    }
}
