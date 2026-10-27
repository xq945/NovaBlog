package com.novablog.service.impl;

import com.novablog.common.PageResult;
import com.novablog.common.UserContext;
import com.novablog.common.exception.BusinessException;
import com.novablog.entity.Article;
import com.novablog.mapper.ArticleFavoriteMapper;
import com.novablog.mapper.ArticleMapper;
import com.novablog.service.FavoriteService;
import com.novablog.vo.ArticleVO;
import com.novablog.vo.FavoriteStatusVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 收藏服务
 *
 * <p>与点赞不同，收藏关系落在 MySQL，计数用原子 SQL 维护，Redis 不参与。
 * 关系行与计数在同一个事务里，任一步失败一起回滚，不会出现「收藏了但计数没加」。
 */
@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {

    private final ArticleMapper articleMapper;
    private final ArticleFavoriteMapper favoriteMapper;

    @Override
    @Transactional
    public FavoriteStatusVO favorite(Long articleId) {
        Long userId = requireLogin();
        Article article = requirePublishedArticle(articleId);

        // 唯一键保证幂等：返回 1 才是本次新增，0 表示早就收藏过
        int inserted = favoriteMapper.insertIgnore(userId, articleId);
        if (inserted > 0) {
            articleMapper.incrementFavoriteCount(articleId);
        }
        // 读回权威计数，而不是在内存里 +1，并发下才不会给出错误的数字
        Article latest = articleMapper.findById(articleId);
        long count = latest == null || latest.getFavoriteCount() == null
            ? 0 : latest.getFavoriteCount();
        return buildStatus(true, count);
    }

    @Override
    @Transactional
    public FavoriteStatusVO unfavorite(Long articleId) {
        Long userId = requireLogin();

        int deleted = favoriteMapper.deleteByUserAndArticle(userId, articleId);
        if (deleted > 0) {
            articleMapper.decrementFavoriteCount(articleId);
        }
        Article article = articleMapper.findById(articleId);
        long count = article == null || article.getFavoriteCount() == null
            ? 0 : article.getFavoriteCount();
        return buildStatus(false, count);
    }

    @Override
    public FavoriteStatusVO getStatus(Long articleId) {
        Article article = articleMapper.findById(articleId);
        if (article == null) {
            throw new BusinessException(404, "文章不存在");
        }
        Long userId = UserContext.getUserId();
        boolean favorited = userId != null && favoriteMapper.exists(userId, articleId) > 0;
        long count = article.getFavoriteCount() == null ? 0 : article.getFavoriteCount();
        return buildStatus(favorited, count);
    }

    @Override
    public PageResult<ArticleVO> listMyFavorites(int page, int size) {
        Long userId = requireLogin();
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        int offset = (safePage - 1) * safeSize;

        List<ArticleVO> list = favoriteMapper.findFavoriteArticles(userId, offset, safeSize);
        Long total = favoriteMapper.countByUserId(userId);
        return new PageResult<>(total == null ? 0L : total, list);
    }

    private Long requireLogin() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        return userId;
    }

    private Article requirePublishedArticle(Long articleId) {
        Article article = articleMapper.findById(articleId);
        if (article == null) {
            throw new BusinessException(404, "文章不存在");
        }
        if (article.getStatus() == null || article.getStatus() != 1) {
            throw new BusinessException(400, "只能收藏已发布的文章");
        }
        return article;
    }

    private FavoriteStatusVO buildStatus(boolean favorited, long count) {
        FavoriteStatusVO vo = new FavoriteStatusVO();
        vo.setFavorited(favorited);
        vo.setFavoriteCount((int) Math.max(count, 0));
        return vo;
    }
}
