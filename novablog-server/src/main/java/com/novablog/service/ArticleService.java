package com.novablog.service;

import com.novablog.common.PageResult;
import com.novablog.dto.ArticleDTO;
import com.novablog.vo.ArticleDetailVO;
import com.novablog.vo.ArticleVO;
import com.novablog.vo.HotArticleVO;
import com.novablog.vo.LikeStatusVO;

import java.util.List;

/**
 * 文章业务层接口
 */
public interface ArticleService {

    /**
     * 发布文章
     *
     * @param articleDTO 文章参数
     * @return 文章ID
     */
    Long publish(ArticleDTO articleDTO);

    /**
     * 查询文章列表（公开）
     *
     * @param page       页码
     * @param size       每页数量
     * @param categoryId 分类ID筛选
     * @param keyword    关键词搜索
     * @return 分页结果
     */
    PageResult<ArticleVO> findList(Integer page, Integer size, Long categoryId, String keyword);

    /**
     * 查询文章详情
     *
     * @param id 文章ID
     * @return 文章详情
     */
    ArticleDetailVO findDetail(Long id);

    /**
     * 修改文章
     *
     * @param articleDTO 文章参数
     */
    void update(ArticleDTO articleDTO);

    /**
     * 删除文章
     *
     * @param id 文章ID
     */
    void delete(Long id);

    /**
     * 查询当前用户的文章列表
     *
     * @param page 页码
     * @param size 每页数量
     * @return 分页结果
     */
    PageResult<ArticleVO> findMyArticles(Integer page, Integer size);

    /**
     * 点赞文章
     *
     * @param articleId 文章ID
     */
    void like(Long articleId);

    /**
     * 取消点赞
     *
     * @param articleId 文章ID
     */
    void unlike(Long articleId);

    /**
     * 查询点赞状态
     *
     * @param articleId 文章ID
     * @return 点赞状态
     */
    LikeStatusVO getLikeStatus(Long articleId);

    /**
     * 查询热门文章列表
     *
     * @param size 数量
     * @return 热门文章列表
     */
    List<HotArticleVO> findHotArticles(Integer size);
}
