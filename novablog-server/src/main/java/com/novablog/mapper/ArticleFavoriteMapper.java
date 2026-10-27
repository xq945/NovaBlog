package com.novablog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novablog.entity.ArticleFavorite;
import com.novablog.vo.ArticleVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ArticleFavoriteMapper extends BaseMapper<ArticleFavorite> {

    /**
     * 插入收藏关系。唯一键冲突时静默跳过，返回 0
     *
     * @return 1-本次新增，0-已收藏
     */
    int insertIgnore(@Param("userId") Long userId, @Param("articleId") Long articleId);

    /**
     * 删除收藏关系
     *
     * @return 1-确实删除了，0-原本没收藏
     */
    int deleteByUserAndArticle(@Param("userId") Long userId, @Param("articleId") Long articleId);

    /**
     * 是否已收藏
     */
    int exists(@Param("userId") Long userId, @Param("articleId") Long articleId);

    /**
     * 我的收藏列表，按收藏时间倒序
     */
    List<ArticleVO> findFavoriteArticles(@Param("userId") Long userId,
                                         @Param("offset") int offset,
                                         @Param("limit") int limit);

    Long countByUserId(@Param("userId") Long userId);

    /**
     * 该用户收藏过的文章 ID，用于删除用户时回收文章的收藏计数
     */
    List<Long> findArticleIdsByUser(@Param("userId") Long userId);
}
