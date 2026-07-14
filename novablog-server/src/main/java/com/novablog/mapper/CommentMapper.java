package com.novablog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novablog.entity.Comment;
import com.novablog.vo.AdminCommentVO;
import com.novablog.vo.CommentVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CommentMapper extends BaseMapper<Comment> {

    Comment selectById(Long id);

    List<CommentVO> selectTopLevelByArticleId(@Param("articleId") Long articleId,
                                               @Param("offset") Integer offset,
                                               @Param("limit") Integer limit);

    Long countTopLevelByArticleId(Long articleId);

    List<CommentVO> selectRepliesByParentIds(@Param("parentIds") List<Long> parentIds);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    int batchUpdateStatusByParentId(@Param("parentId") Long parentId,
                                    @Param("status") Integer status);

    List<AdminCommentVO> findAdminList(@Param("articleId") Long articleId,
                                       @Param("offset") int offset,
                                       @Param("size") int size);

    Long countAdminList(@Param("articleId") Long articleId);
}
