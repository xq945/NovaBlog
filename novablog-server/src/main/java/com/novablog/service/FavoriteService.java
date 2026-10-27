package com.novablog.service;

import com.novablog.common.PageResult;
import com.novablog.vo.ArticleVO;
import com.novablog.vo.FavoriteStatusVO;

public interface FavoriteService {

    /**
     * 收藏文章。重复收藏幂等，不重复计数
     */
    FavoriteStatusVO favorite(Long articleId);

    /**
     * 取消收藏。未收藏时取关同样幂等
     */
    FavoriteStatusVO unfavorite(Long articleId);

    /**
     * 查询当前用户对文章的收藏状态
     */
    FavoriteStatusVO getStatus(Long articleId);

    /**
     * 我的收藏列表
     */
    PageResult<ArticleVO> listMyFavorites(int page, int size);
}
