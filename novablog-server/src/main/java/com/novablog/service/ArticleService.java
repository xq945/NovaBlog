package com.novablog.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.novablog.common.PageResult;
import com.novablog.dto.request.ArticleDTO;
import com.novablog.entity.Article;
import com.novablog.vo.ArticleDetailVO;
import com.novablog.vo.ArticleVO;
import com.novablog.vo.HotArticleVO;
import com.novablog.vo.LikeStatusVO;

import java.util.List;

public interface ArticleService extends IService<Article> {

    Long publish(ArticleDTO articleDTO);

    PageResult<ArticleVO> findList(Integer page, Integer size, Long categoryId, String keyword);

    ArticleDetailVO findDetail(Long id);

    void update(ArticleDTO articleDTO);

    void delete(Long id);

    PageResult<ArticleVO> findMyArticles(Integer page, Integer size);

    void like(Long articleId);

    void unlike(Long articleId);

    LikeStatusVO getLikeStatus(Long articleId);

    List<HotArticleVO> findHotArticles(Integer size);

    PageResult<ArticleVO> findAdminList(Integer page, Integer size, String keyword);
}
