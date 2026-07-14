package com.novablog.controller;

import com.novablog.common.PageResult;
import com.novablog.common.Result;
import com.novablog.common.annotation.RequireAdmin;
import com.novablog.dto.request.CommentDTO;
import com.novablog.service.CommentService;
import com.novablog.vo.AdminCommentVO;
import com.novablog.vo.CommentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 评论控制器
 */
@RestController
@RequestMapping("/comment")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    /**
     * 发表评论
     *
     * @param dto 评论参数
     * @return 评论VO
     */
    @PostMapping
    public Result<CommentVO> publish(@RequestBody CommentDTO dto) {
        CommentVO vo = commentService.publish(dto);
        return Result.success(vo);
    }

    /**
     * 查询评论列表
     *
     * @param articleId 文章ID
     * @param page      页码
     * @param size      每页数量
     * @return 分页结果
     */
    @GetMapping("/list")
    public Result<PageResult<CommentVO>> list(
            @RequestParam Long articleId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size) {
        PageResult<CommentVO> result = commentService.findList(articleId, page, size);
        return Result.success(result);
    }

    /**
     * 删除评论
     *
     * @param id 评论ID
     * @return 成功结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        commentService.delete(id);
        return Result.success();
    }

    /**
     * 管理员查询所有评论
     *
     * @param articleId 文章ID筛选
     * @param page      页码
     * @param size      每页数量
     * @return 分页结果
     */
    @GetMapping("/admin/list")
    @RequireAdmin
    public Result<PageResult<AdminCommentVO>> adminList(
            @RequestParam(required = false) Long articleId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size) {
        PageResult<AdminCommentVO> result = commentService.findAdminList(articleId, page, size);
        return Result.success(result);
    }
}
