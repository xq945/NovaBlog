package com.novablog.service.converter;

import com.novablog.entity.Article;
import com.novablog.vo.ArticleDetailVO;
import com.novablog.vo.ArticleVO;
import com.novablog.vo.HotArticleVO;

/**
 * 文章对象转换器
 * 负责 Article 实体与各类 VO 之间的转换
 */
public final class ArticleConverter {

    private ArticleConverter() {
        // 禁止实例化
    }

    /**
     * 将 Article 实体转换为列表项 VO
     *
     * @param article 文章实体
     * @return 文章列表项 VO
     */
    public static ArticleVO toArticleVO(Article article) {
        if (article == null) {
            return null;
        }
        ArticleVO vo = new ArticleVO();
        vo.setId(article.getId());
        vo.setTitle(article.getTitle());
        vo.setSummary(article.getSummary());
        vo.setCover(article.getCover());
        vo.setViewCount(article.getViewCount() != null ? article.getViewCount().intValue() : 0);
        vo.setLikeCount(article.getLikeCount() != null ? article.getLikeCount().intValue() : 0);
        vo.setStatus(article.getStatus());
        vo.setCreateTime(article.getCreateTime());
        return vo;
    }

    /**
     * 将 Article 实体转换为详情 VO（基础字段）
     *
     * @param article 文章实体
     * @return 文章详情 VO
     */
    public static ArticleDetailVO toArticleDetailVO(Article article) {
        if (article == null) {
            return null;
        }
        ArticleDetailVO vo = new ArticleDetailVO();
        vo.setId(article.getId());
        vo.setTitle(article.getTitle());
        vo.setContent(article.getContent());
        vo.setSummary(article.getSummary());
        vo.setCover(article.getCover());
        vo.setViewCount(article.getViewCount() != null ? article.getViewCount().intValue() : 0);
        vo.setLikeCount(article.getLikeCount() != null ? article.getLikeCount().intValue() : 0);
        vo.setStatus(article.getStatus());
        vo.setCreateTime(article.getCreateTime());
        vo.setUpdateTime(article.getUpdateTime());
        return vo;
    }

    /**
     * 将 ArticleVO 转换为热门文章 VO
     *
     * @param articleVO 文章列表项 VO
     * @return 热门文章 VO
     */
    public static HotArticleVO toHotArticleVO(ArticleVO articleVO) {
        if (articleVO == null) {
            return null;
        }
        HotArticleVO hotVO = new HotArticleVO();
        hotVO.setId(articleVO.getId());
        hotVO.setTitle(articleVO.getTitle());
        hotVO.setSummary(articleVO.getSummary());
        hotVO.setCover(articleVO.getCover());
        hotVO.setViewCount(articleVO.getViewCount());
        hotVO.setLikeCount(articleVO.getLikeCount());
        hotVO.setStatus(articleVO.getStatus());
        hotVO.setAuthor(articleVO.getAuthor());
        hotVO.setCategory(articleVO.getCategory());
        hotVO.setTags(articleVO.getTags());
        hotVO.setCreateTime(articleVO.getCreateTime());
        return hotVO;
    }
}
