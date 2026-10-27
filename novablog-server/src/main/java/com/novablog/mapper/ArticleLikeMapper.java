package com.novablog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novablog.entity.ArticleLike;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ArticleLikeMapper extends BaseMapper<ArticleLike> {

    /**
     * 插入点赞关系。唯一键冲突时静默跳过，返回 0
     *
     * @return 1-本次新增，0-已点赞
     */
    int insertIgnore(@Param("userId") Long userId, @Param("articleId") Long articleId);

    /**
     * 批量插入点赞关系，用于启动时把 Redis 里已有的明细回填到数据库
     *
     * @return 实际新增的行数
     */
    int batchInsertIgnore(@Param("articleId") Long articleId,
                          @Param("userIds") List<Long> userIds);

    /**
     * 删除点赞关系
     *
     * @return 1-确实删除，0-原本没点赞
     */
    int deleteByUserAndArticle(@Param("userId") Long userId, @Param("articleId") Long articleId);

    /**
     * 是否已点赞
     */
    int exists(@Param("userId") Long userId, @Param("articleId") Long articleId);

    /**
     * 某篇文章的全部点赞用户，用于重建 Redis 缓存
     */
    List<Long> findUserIdsByArticle(@Param("articleId") Long articleId);

    /**
     * 某篇文章的点赞数，关系表是计数正本
     */
    int countByArticle(@Param("articleId") Long articleId);
}
