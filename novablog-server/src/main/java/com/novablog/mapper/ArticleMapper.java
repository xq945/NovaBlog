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

    int batchUpdateViewCount(@Param("articleIdList") List<Long> articleIdList,
                              @Param("viewCountList") List<Long> viewCountList);

    int batchUpdateLikeCount(@Param("articleIdList") List<Long> articleIdList,
                              @Param("likeCountList") List<Long> likeCountList);

    Long countByIndexedStatus(@Param("indexed") Integer indexed);

    List<Article> findByIndexedStatus(@Param("indexed") Integer indexed);

    List<Article> findAllPublished();

    List<ArticleVO> findByIds(@Param("ids") List<Long> ids);

    List<ArticleVO> findAdminList(@Param("keyword") String keyword,
                                   @Param("offset") int offset,
                                   @Param("size") int size);

    Long countAdminList(@Param("keyword") String keyword);
}
