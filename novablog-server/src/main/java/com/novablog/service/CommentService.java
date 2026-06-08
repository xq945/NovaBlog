package com.novablog.service;

import com.novablog.common.PageResult;
import com.novablog.dto.CommentDTO;
import com.novablog.vo.CommentVO;

/**
 * 评论业务层接口
 */
public interface CommentService {

    /**
     * 发表评论
     *
     * @param dto 评论参数
     * @return 评论VO
     */
    CommentVO publish(CommentDTO dto);

    /**
     * 查询评论列表
     *
     * @param articleId 文章ID
     * @param page      页码
     * @param size      每页数量
     * @return 分页结果
     */
    PageResult<CommentVO> findList(Long articleId, Integer page, Integer size);

    /**
     * 删除评论
     *
     * @param id 评论ID
     */
    void delete(Long id);

    /**
     * 管理员查询所有评论
     *
     * @param articleId 文章ID筛选
     * @param page      页码
     * @param size      每页数量
     * @return 分页结果
     */
    PageResult<com.novablog.vo.AdminCommentVO> findAdminList(Long articleId, Integer page, Integer size);
}
