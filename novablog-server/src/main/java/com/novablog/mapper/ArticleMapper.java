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

    /**
     * 更新浏览量（定时任务同步用）
     *
     * @param id        文章ID
     * @param viewCount 浏览量
     * @return 影响行数
     */
    int updateViewCount(@Param("id") Long id, @Param("viewCount") Long viewCount);

    /**
     * 批量更新浏览量（定时任务同步用，效率高于逐条更新）
     *
     * @param articleIdList  文章ID列表
     * @param viewCountList  对应浏览量列表
     * @return 影响行数
     */
    int batchUpdateViewCount(@Param("articleIdList") List<Long> articleIdList,
                              @Param("viewCountList") List<Long> viewCountList);

    /**
     * 查询所有已发布文章（Redis初始化用）
     *
     * @return 文章列表
     */
    List<Article> findAllPublished();

    /**
     * 根据ID列表批量查询文章（热门文章用）
     *
     * @param ids 文章ID列表
     * @return 文章列表
     */
    List<ArticleVO> findByIds(@Param("ids") List<Long> ids);

    /**
     * 管理员查询所有文章（含草稿）
     *
     * @param keyword 关键词搜索，可为null
     * @param offset  偏移量
     * @param size    每页数量
     * @return 文章列表
     */
    List<ArticleVO> findAdminList(@Param("keyword") String keyword,
                                   @Param("offset") int offset,
                                   @Param("size") int size);

    /**
     * 管理员查询文章总数
     *
     * @param keyword 关键词搜索，可为null
     * @return 总记录数
     */
    Long countAdminList(@Param("keyword") String keyword);
}
