package com.novablog.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.novablog.common.PageResult;
import com.novablog.dto.CommentDTO;
import com.novablog.entity.Comment;
import com.novablog.vo.AdminCommentVO;
import com.novablog.vo.CommentVO;

public interface CommentService extends IService<Comment> {

    CommentVO publish(CommentDTO dto);

    PageResult<CommentVO> findList(Long articleId, Integer page, Integer size);

    void delete(Long id);

    PageResult<AdminCommentVO> findAdminList(Long articleId, Integer page, Integer size);
}
