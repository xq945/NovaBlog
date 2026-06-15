package com.novablog.service.impl;

import com.novablog.common.PageResult;
import com.novablog.common.UserContext;
import com.novablog.common.constant.RoleConstant;
import com.novablog.common.exception.BusinessException;
import com.novablog.dto.CommentDTO;
import com.novablog.entity.Article;
import com.novablog.entity.Comment;
import com.novablog.entity.User;
import com.novablog.mapper.ArticleMapper;
import com.novablog.mapper.CommentMapper;
import com.novablog.mapper.UserMapper;
import com.novablog.service.CommentService;
import com.novablog.vo.AdminCommentVO;
import com.novablog.vo.CommentVO;
import com.novablog.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 评论业务层实现
 */
@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentMapper commentMapper;
    private final UserMapper userMapper;
    private final ArticleMapper articleMapper;

    /**
     * 评论内容最大长度
     */
    private static final int CONTENT_MAX_LENGTH = 500;

    @Override
    public CommentVO publish(CommentDTO dto) {
        // 1. 参数校验
        String content = dto.getContent();
        if (content == null) {
            throw new BusinessException(400, "评论内容不能为空");
        }
        content = content.trim();
        if (content.isEmpty()) {
            throw new BusinessException(400, "评论内容不能为空");
        }
        if (content.length() > CONTENT_MAX_LENGTH) {
            throw new BusinessException(400, "评论内容不能超过500字");
        }

        Long articleId = dto.getArticleId();
        if (articleId == null) {
            throw new BusinessException(400, "文章ID不能为空");
        }

        Long parentId = dto.getParentId();
        Long replyToId = dto.getReplyToId();
        if (parentId == null && replyToId != null) {
            throw new BusinessException(400, "参数错误");
        }

        // 2. 校验文章
        Article article = articleMapper.findById(articleId);
        if (article == null) {
            throw new BusinessException(404, "文章不存在");
        }
        if (article.getStatus() != null && article.getStatus() == 0) {
            throw new BusinessException(400, "草稿文章禁止评论");
        }

        // 3. 若 parentId 不为空，校验父评论
        if (parentId != null) {
            Comment parentComment = commentMapper.selectById(parentId);
            if (parentComment == null) {
                throw new BusinessException(400, "回复的评论不存在");
            }
            if (parentComment.getStatus() == null || parentComment.getStatus() == 0) {
                throw new BusinessException(400, "回复的评论已删除");
            }
            if (parentComment.getParentId() != null) {
                throw new BusinessException(400, "只能回复一级评论");
            }
            if (!articleId.equals(parentComment.getArticleId())) {
                throw new BusinessException(400, "回复的评论与文章不匹配");
            }
            // 若 replyToId 为空，默认取父评论的 user_id
            if (replyToId == null) {
                replyToId = parentComment.getUserId();
            }
        }

        // 4. 若 replyToId 不为空，校验被回复用户是否存在
        if (replyToId != null) {
            User replyUser = userMapper.findById(replyToId);
            if (replyUser == null) {
                throw new BusinessException(400, "被回复用户不存在");
            }
        }

        // 5. 插入评论
        Comment comment = new Comment();
        comment.setContent(content);
        comment.setArticleId(articleId);
        comment.setUserId(UserContext.getUserId());
        comment.setParentId(parentId);
        comment.setReplyToId(replyToId);
        commentMapper.insert(comment);

        // 重新查询获取数据库生成的 createTime
        Comment inserted = commentMapper.selectById(comment.getId());

        // 6. 组装 VO
        CommentVO vo = new CommentVO();
        vo.setId(inserted.getId());
        vo.setContent(inserted.getContent());
        vo.setArticleId(inserted.getArticleId());
        vo.setParentId(inserted.getParentId());
        vo.setCreateTime(inserted.getCreateTime());
        vo.setChildren(new ArrayList<>());

        // 查询当前用户信息
        User currentUser = userMapper.findById(UserContext.getUserId());
        UserVO userVO = new UserVO();
        if (currentUser != null) {
            userVO.setId(currentUser.getId());
            userVO.setNickname(currentUser.getNickname());
            userVO.setAvatar(currentUser.getAvatar());
        } else {
            userVO.setId(UserContext.getUserId());
            userVO.setNickname(UserContext.getUsername());
            userVO.setAvatar(null);
        }
        vo.setUser(userVO);

        // 查询被回复用户信息
        if (replyToId != null) {
            User replyUser = userMapper.findById(replyToId);
            if (replyUser != null) {
                UserVO replyUserVO = new UserVO();
                replyUserVO.setId(replyUser.getId());
                replyUserVO.setNickname(replyUser.getNickname());
                vo.setReplyToUser(replyUserVO);
            }
        }

        return vo;
    }

    @Override
    public PageResult<CommentVO> findList(Long articleId, Integer page, Integer size) {
        if (articleId == null) {
            throw new BusinessException(400, "文章ID不能为空");
        }

        // 校验文章存在且已发布；不存在或草稿返回空列表
        Article article = articleMapper.findById(articleId);
        if (article == null || article.getStatus() == null || article.getStatus() == 0) {
            return new PageResult<>(0L, Collections.emptyList());
        }

        // 修正分页参数
        if (page == null || page < 1) {
            page = 1;
        }
        if (size == null || size < 1) {
            size = 10;
        }
        if (size > 50) {
            size = 50;
        }
        int offset = (page - 1) * size;

        // 查询一级评论
        List<CommentVO> topList = commentMapper.selectTopLevelByArticleId(articleId, offset, size);
        Long total = commentMapper.countTopLevelByArticleId(articleId);

        // 查询二级回复
        if (!topList.isEmpty()) {
            List<Long> parentIds = topList.stream()
                    .map(CommentVO::getId)
                    .collect(Collectors.toList());
            List<CommentVO> replies = commentMapper.selectRepliesByParentIds(parentIds);

            Map<Long, List<CommentVO>> replyMap = replies.stream()
                    .collect(Collectors.groupingBy(CommentVO::getParentId));

            for (CommentVO top : topList) {
                top.setChildren(replyMap.getOrDefault(top.getId(), new ArrayList<>()));
            }
        }

        return new PageResult<>(total, topList);
    }

    @Override
    public PageResult<AdminCommentVO> findAdminList(Long articleId, Integer page, Integer size) {
        // 修正分页参数
        if (page == null || page < 1) {
            page = 1;
        }
        if (size == null || size < 1) {
            size = 10;
        }
        if (size > 50) {
            size = 50;
        }
        int offset = (page - 1) * size;

        List<AdminCommentVO> list = commentMapper.findAdminList(articleId, offset, size);
        Long total = commentMapper.countAdminList(articleId);

        return new PageResult<>(total, list);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        // 1. 查询评论
        Comment comment = commentMapper.selectById(id);
        if (comment == null || comment.getStatus() == null || comment.getStatus() == 0) {
            throw new BusinessException(404, "评论不存在");
        }

        // 2. 权限校验
        Long currentUserId = UserContext.getUserId();
        String currentRole = UserContext.getRole();
        if (!Objects.equals(comment.getUserId(), currentUserId) && !RoleConstant.ADMIN.equals(currentRole)) {
            throw new BusinessException(403, "无权删除该评论");
        }

        // 3. 逻辑删除自身
        commentMapper.updateStatus(id, 0);

        // 4. 若是一级评论，级联删除二级回复
        if (comment.getParentId() == null) {
            commentMapper.batchUpdateStatusByParentId(id, 0);
        }
    }
}
