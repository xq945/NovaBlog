package com.novablog.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novablog.common.PageResult;
import com.novablog.common.Result;
import com.novablog.common.UserContext;
import com.novablog.common.exception.BusinessException;
import com.novablog.entity.Article;
import com.novablog.entity.Favorite;
import com.novablog.mapper.ArticleMapper;
import com.novablog.mapper.FavoriteMapper;
import com.novablog.util.RedisUtil;
import com.novablog.vo.ArticleVO;
import com.novablog.vo.FavoriteArticleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文章收藏控制器
 * 收藏数据以 MySQL 主存，Redis 作为读缓存（Cache-Aside）
 */
@RestController
@RequestMapping("/favorite")
@RequiredArgsConstructor
public class FavoriteController {

    private static final String COUNT_KEY = "article:favorite:count:";
    private static final String USER_KEY  = "article:favorite:user:";

    private final FavoriteMapper favoriteMapper;
    private final ArticleMapper articleMapper;
    private final RedisUtil redisUtil;

    /**
     * 收藏文章
     */
    @PostMapping("/{articleId}")
    public Result<Map<String, Object>> favorite(@PathVariable Long articleId) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }

        // 校验文章是否存在且已发布
        Article article = articleMapper.selectById(articleId);
        if (article == null) {
            throw new BusinessException(404, "文章不存在");
        }
        if (article.getStatus() == null || article.getStatus() != 1) {
            throw new BusinessException(400, "草稿文章禁止操作");
        }

        // 校验是否已收藏
        Favorite existing = favoriteMapper.selectOne(
                new LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getUserId, userId)
                        .eq(Favorite::getArticleId, articleId));
        if (existing != null) {
            throw new BusinessException(400, "已收藏过该文章");
        }

        // 写 MySQL
        Favorite fav = new Favorite();
        fav.setUserId(userId);
        fav.setArticleId(articleId);
        favoriteMapper.insert(fav);

        // 刷新 Redis 缓存
        long count = refreshCountCache(articleId);
        redisUtil.set(USER_KEY + userId + ":" + articleId, "1");

        return Result.success(Map.of("favorited", true, "favoriteCount", count));
    }

    /**
     * 取消收藏
     */
    @DeleteMapping("/{articleId}")
    public Result<Map<String, Object>> unfavorite(@PathVariable Long articleId) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }

        Favorite existing = favoriteMapper.selectOne(
                new LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getUserId, userId)
                        .eq(Favorite::getArticleId, articleId));
        if (existing == null) {
            throw new BusinessException(400, "未收藏该文章");
        }

        // 写 MySQL
        favoriteMapper.deleteById(existing.getId());

        // 刷新 Redis 缓存
        long count = refreshCountCache(articleId);
        redisUtil.del(USER_KEY + userId + ":" + articleId);

        return Result.success(Map.of("favorited", false, "favoriteCount", count));
    }

    /**
     * 查询收藏状态（公开，未登录返回 favorited: false）
     * 优先从 Redis 读取，Cache-Aside 模式
     */
    @GetMapping("/{articleId}")
    public Result<Map<String, Object>> status(@PathVariable Long articleId) {
        // 校验文章
        Article article = articleMapper.selectById(articleId);
        if (article == null) {
            throw new BusinessException(404, "文章不存在");
        }

        // Cache-Aside: 查 count
        String countStr = redisUtil.get(COUNT_KEY + articleId);
        long count;
        if (countStr != null) {
            count = Long.parseLong(countStr);
        } else {
            count = favoriteMapper.selectCount(
                    new LambdaQueryWrapper<Favorite>()
                            .eq(Favorite::getArticleId, articleId));
            redisUtil.set(COUNT_KEY + articleId, String.valueOf(count));
        }

        // 查用户收藏状态
        boolean favorited = false;
        Long userId = UserContext.getUserId();
        if (userId != null) {
            String cached = redisUtil.get(USER_KEY + userId + ":" + articleId);
            if (cached != null) {
                favorited = true;
            } else {
                Favorite fav = favoriteMapper.selectOne(
                        new LambdaQueryWrapper<Favorite>()
                                .eq(Favorite::getUserId, userId)
                                .eq(Favorite::getArticleId, articleId));
                if (fav != null) {
                    favorited = true;
                    redisUtil.set(USER_KEY + userId + ":" + articleId, "1");
                }
            }
        }

        return Result.success(Map.of("favorited", favorited, "favoriteCount", count));
    }

    /**
     * 我的收藏列表
     */
    @GetMapping("/list")
    public Result<PageResult<FavoriteArticleVO>> myFavorites(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }

        // 分页查询收藏记录（按收藏时间倒序）
        Page<Favorite> favPage = favoriteMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getUserId, userId)
                        .orderByDesc(Favorite::getCreateTime));

        if (favPage.getRecords().isEmpty()) {
            return Result.success(new PageResult<>(favPage.getTotal(), List.of()));
        }

        // 批量查询文章信息（避免 N+1）
        List<Long> articleIds = favPage.getRecords().stream()
                .map(Favorite::getArticleId)
                .collect(Collectors.toList());
        List<ArticleVO> articleList = articleMapper.findByIds(articleIds);
        Map<Long, ArticleVO> articleMap = articleList.stream()
                .collect(Collectors.toMap(ArticleVO::getId, a -> a));

        // 组装 VO
        List<FavoriteArticleVO> records = favPage.getRecords().stream()
                .map(fav -> {
                    ArticleVO articleVO = articleMap.get(fav.getArticleId());
                    FavoriteArticleVO vo = new FavoriteArticleVO();
                    if (articleVO != null) {
                        vo.setId(articleVO.getId());
                        vo.setTitle(articleVO.getTitle());
                        vo.setSummary(articleVO.getSummary());
                        vo.setCover(articleVO.getCover());
                        vo.setViewCount(articleVO.getViewCount());
                        vo.setLikeCount(articleVO.getLikeCount());
                        vo.setAuthor(articleVO.getAuthor());
                        vo.setCategory(articleVO.getCategory());
                        vo.setCreateTime(articleVO.getCreateTime());
                    } else {
                        // 文章可能已被删除，仍显示收藏记录但标记
                        vo.setId(fav.getArticleId());
                        vo.setTitle("[文章已删除]");
                    }
                    vo.setFavoriteTime(fav.getCreateTime());
                    return vo;
                }).toList();

        return Result.success(new PageResult<>(favPage.getTotal(), records));
    }

    /**
     * 刷新 count 缓存（写操作后调用）
     */
    private long refreshCountCache(Long articleId) {
        long count = favoriteMapper.selectCount(
                new LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getArticleId, articleId));
        redisUtil.set(COUNT_KEY + articleId, String.valueOf(count));
        return count;
    }
}
