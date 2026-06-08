package com.novablog.mapper;

import com.novablog.entity.Comment;
import com.novablog.vo.AdminCommentVO;
import com.novablog.vo.CommentVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 评论数据访问层
 */
@Mapper
public interface CommentMapper {

    /**
     * 插入评论
     *
     * @param comment 评论实体
     * @return 影响行数
     */
    int insert(Comment comment);

    /**
     * 根据ID查询评论（实体，用于业务校验）
     *
     * @param id 评论ID
     * @return 评论实体
     */
    Comment selectById(Long id);

    /**
     * 分页查询某文章下的一级评论（JOIN user 表获取评论者信息）
     *
     * @param articleId 文章ID
     * @param offset    偏移量
     * @param limit     每页数量
     * @return 一级评论列表
     */
    List<CommentVO> selectTopLevelByArticleId(@Param("articleId") Long articleId,
                                               @Param("offset") Integer offset,
                                               @Param("limit") Integer limit);

    /**
     * 统计某文章下的一级评论总数
     *
     * @param articleId 文章ID
     * @return 总数
     */
    Long countTopLevelByArticleId(Long articleId);

    /**
     * 批量查询一级评论下的二级回复（JOIN user 表获取评论者信息）
     *
     * @param parentIds 父评论ID列表
     * @return 二级回复列表
     */
    List<CommentVO> selectRepliesByParentIds(@Param("parentIds") List<Long> parentIds);

    /**
     * 更新评论状态
     *
     * @param id     评论ID
     * @param status 状态
     * @return 影响行数
     */
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /**
     * 根据 parentId 批量更新状态（级联删除）
     *
     * @param parentId 父评论ID
     * @param status   状态
     * @return 影响行数
     */
    int batchUpdateStatusByParentId(@Param("parentId") Long parentId,
                                    @Param("status") Integer status);

    /**
     * 管理员查询所有评论（扁平化，JOIN 文章标题）
     *
     * @param articleId 文章ID筛选，可为null
     * @param offset    偏移量
     * @param size      每页数量
     * @return 评论列表
     */
    List<AdminCommentVO> findAdminList(@Param("articleId") Long articleId,
                                       @Param("offset") int offset,
                                       @Param("size") int size);

    /**
     * 管理员查询评论总数
     *
     * @param articleId 文章ID筛选，可为null
     * @return 总数
     */
    Long countAdminList(@Param("articleId") Long articleId);
}
