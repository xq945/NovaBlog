package com.novablog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novablog.entity.Article;
import com.novablog.vo.ArticleDetailVO;
import com.novablog.vo.ArticleVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ArticleMapper extends BaseMapper<Article> {

    Article findById(Long id);

    ArticleDetailVO findDetailById(Long id);

    List<ArticleVO> findList(@Param("categoryId") Long categoryId,
                              @Param("keyword") String keyword,
                              @Param("offset") Integer offset,
                              @Param("limit") Integer limit);

    Long countList(@Param("categoryId") Long categoryId,
                    @Param("keyword") String keyword);

    List<ArticleVO> findByUserId(@Param("userId") Long userId,
                                  @Param("offset") Integer offset,
                                  @Param("limit") Integer limit);

    Long countByUserId(@Param("userId") Long userId);

    int update(Article article);

    int incrementViewCount(Long id);

    int updateViewCount(@Param("id") Long id, @Param("viewCount") Long viewCount);

    int updateLikeCount(@Param("id") Long id, @Param("likeCount") Long likeCount);

    /**
     * 点赞数原子加一，避免 read-modify-write 在并发下丢更新
     */
    int incrementLikeCount(@Param("id") Long id);

    /**
     * 点赞数原子减一，用 GREATEST 兜底，避免并发下减成负数
     */
    int decrementLikeCount(@Param("id") Long id);

    /**
     * 收藏数加一。原子 SQL，不做 read-modify-write
     */
    int incrementFavoriteCount(@Param("id") Long id);

    /**
     * 收藏数减一，用 GREATEST 兜底，避免并发下减成负数
     */
    int decrementFavoriteCount(@Param("id") Long id);

    int batchUpdateViewCount(@Param("articleIdList") List<Long> articleIdList,
                              @Param("viewCountList") List<Long> viewCountList);

    int batchUpdateLikeCount(@Param("articleIdList") List<Long> articleIdList,
                              @Param("likeCountList") List<Long> likeCountList);

    Long countByIndexedStatus(@Param("indexed") Integer indexed);

    List<Article> findByIndexedStatus(@Param("indexed") Integer indexed);

    List<Article> findAllPublished();

    List<ArticleVO> findByIds(@Param("ids") List<Long> ids);

    /**
     * 只查统计同步需要的 id、浏览量、点赞数，避免为了比较计数去联表查整行
     */
    List<Article> findStatsByIds(@Param("ids") List<Long> ids);

    /**
     * 按数据库里的浏览量、点赞数计算热度并排序，用于 Redis 不可用时兜底
     */
    List<ArticleVO> findHotFromDb(@Param("limit") int limit);

    List<ArticleVO> findAdminList(@Param("keyword") String keyword,
                                   @Param("offset") int offset,
                                   @Param("size") int size);

    Long countAdminList(@Param("keyword") String keyword);
}
