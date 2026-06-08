package com.novablog.mapper;

import com.novablog.entity.Article;
import com.novablog.vo.ArticleDetailVO;
import com.novablog.vo.ArticleVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文章数据访问层
 */
@Mapper
public interface ArticleMapper {

    /**
     * 插入文章
     *
     * @param article 文章实体
     * @return 影响行数
     */
    int insert(Article article);

    /**
     * 根据ID查询文章
     *
     * @param id 文章ID
     * @return 文章实体
     */
    Article findById(Long id);

    /**
     * 根据ID查询文章详情（含作者、分类、标签）
     *
     * @param id 文章ID
     * @return 文章详情VO
     */
    ArticleDetailVO findDetailById(Long id);

    /**
     * 分页查询文章列表（公开）
     *
     * @param categoryId 分类ID筛选，可为null
     * @param keyword    关键词搜索（标题/摘要），可为null
     * @param offset     偏移量
     * @param limit      每页数量
     * @return 文章列表
     */
    List<ArticleVO> findList(@Param("categoryId") Long categoryId,
                              @Param("keyword") String keyword,
                              @Param("offset") Integer offset,
                              @Param("limit") Integer limit);

    /**
     * 查询文章总数（公开）
     *
     * @param categoryId 分类ID筛选，可为null
     * @param keyword    关键词搜索，可为null
     * @return 总记录数
     */
    Long countList(@Param("categoryId") Long categoryId,
                    @Param("keyword") String keyword);

    /**
     * 查询当前用户的文章列表（包含草稿）
     *
     * @param userId 用户ID
     * @param offset 偏移量
     * @param limit  每页数量
     * @return 文章列表
     */
    List<ArticleVO> findByUserId(@Param("userId") Long userId,
                                  @Param("offset") Integer offset,
                                  @Param("limit") Integer limit);

    /**
     * 查询当前用户的文章总数
     *
     * @param userId 用户ID
     * @return 总记录数
     */
    Long countByUserId(@Param("userId") Long userId);

    /**
     * 更新文章
     *
     * @param article 文章实体
     * @return 影响行数
     */
    int update(Article article);

    /**
     * 删除文章
     *
     * @param id 文章ID
     * @return 影响行数
     */
    int deleteById(Long id);

    /**
     * 浏览量 +1（原生SQL，不触发 update_time 更新）
     *
     * @param id 文章ID
     * @return 影响行数
     */
    int incrementViewCount(Long id);
}
