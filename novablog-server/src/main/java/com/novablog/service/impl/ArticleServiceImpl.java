package com.novablog.service.impl;

import com.novablog.common.PageResult;
import com.novablog.common.UserContext;
import com.novablog.common.constant.RedisKeyConstant;
import com.novablog.common.constant.RoleConstant;
import com.novablog.common.exception.BusinessException;
import com.novablog.dto.ArticleDTO;
import com.novablog.dto.ArticleTagDTO;
import com.novablog.entity.Article;
import com.novablog.entity.Category;
import com.novablog.entity.Tag;
import com.novablog.mapper.ArticleMapper;
import com.novablog.mapper.ArticleTagMapper;
import com.novablog.mapper.CategoryMapper;
import com.novablog.mapper.TagMapper;
import com.novablog.service.ArticleService;
import com.novablog.service.component.ArticlePermissionComponent;
import com.novablog.service.component.ArticleStatsComponent;
import com.novablog.service.component.TagRelationComponent;
import com.novablog.util.RedisUtil;
import com.novablog.vo.ArticleDetailVO;
import com.novablog.vo.ArticleVO;
import com.novablog.vo.HotArticleVO;
import com.novablog.vo.LikeStatusVO;
import com.novablog.vo.TagVO;
import com.novablog.rag.event.ArticlePublishedEvent;
import com.novablog.rag.service.ArticleIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 文章业务层实现
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ArticleServiceImpl implements ArticleService {

    private final ArticleMapper articleMapper;
    private final ArticleTagMapper articleTagMapper;
    private final TagMapper tagMapper;
    private final CategoryMapper categoryMapper;
    private final RedisUtil redisUtil;
    private final ArticleStatsComponent articleStatsComponent;
    private final TagRelationComponent tagRelationComponent;
    private final ArticlePermissionComponent articlePermissionComponent;
    private final ApplicationEventPublisher eventPublisher;
    private final ArticleIndexService articleIndexService;

    /**
     * 摘要自动截取长度
     */
    private static final int SUMMARY_MAX_LENGTH = 150;

    /**
     * 热度分计算权重：浏览量权重
     */
    private static final double VIEW_WEIGHT = 1.0;

    /**
     * 热度分计算权重：点赞数权重
     */
    private static final double LIKE_WEIGHT = 3.0;

    @Override
    @Transactional
    public Long publish(ArticleDTO articleDTO) {
        // 1. 参数校验
        String title = articleDTO.getTitle();
        String content = articleDTO.getContent();
        if (title == null || title.isEmpty() || title.length() > 100) {
            throw new BusinessException(400, "标题不能为空且长度不能超过100字符");
        }
        if (content == null || content.isEmpty() || content.length() > 50000) {
            throw new BusinessException(400, "正文不能为空且长度不能超过50000字符");
        }
        if (articleDTO.getCategoryId() == null) {
            throw new BusinessException(400, "分类不能为空");
        }

        // 2. 校验分类是否存在
        Category category = categoryMapper.findById(articleDTO.getCategoryId());
        if (category == null) {
            throw new BusinessException(400, "分类不存在");
        }

        // 3. 处理摘要
        String summary = articleDTO.getSummary();
        if (summary == null || summary.isEmpty()) {
            summary = extractSummary(content);
        } else if (summary.length() > 500) {
            throw new BusinessException(400, "摘要长度不能超过500字符");
        }

        // 4. 处理状态（默认已发布，非法值视为已发布）
        Integer status = articleDTO.getStatus();
        if (status == null || (status != 0 && status != 1)) {
            status = 1;
        }

        // 5. 构建文章实体
        Article article = new Article();
        article.setTitle(title);
        article.setContent(content);
        article.setSummary(summary);
        article.setCover(articleDTO.getCover());
        article.setUserId(UserContext.getUserId());
        article.setCategoryId(articleDTO.getCategoryId());
        article.setStatus(status);

        // 6. 插入文章
        articleMapper.insert(article);
        Long articleId = article.getId();

        // 7. 关联标签（去重、过滤null、限制数量）
        List<Long> tagIds = articleDTO.getTagIds();
        if (tagIds != null && !tagIds.isEmpty()) {
            List<Long> validTagIds = tagRelationComponent.filterValidTagIds(tagIds);
            if (!validTagIds.isEmpty()) {
                articleTagMapper.batchInsert(articleId, validTagIds);
            }
        }

        // 8. 初始化 Redis 浏览量（文章发布时 view_count 为 0）
        articleStatsComponent.initViewCount(articleId);

        // 9. 触发向量索引（异步）
        eventPublisher.publishEvent(new ArticlePublishedEvent(articleId, true));

        return articleId;
    }

    @Override
    public PageResult<ArticleVO> findList(Integer page, Integer size, Long categoryId, String keyword) {
        // 1. 分页参数修正
        if (page == null || page < 1) {
            page = 1;
        }
        if (size == null || size < 1) {
            size = 10;
        }
        if (size > 50) {
            size = 50;
        }

        // 2. 处理关键词：去除首尾空格，空字符串视为未输入
        if (keyword != null) {
            keyword = keyword.trim();
            if (keyword.isEmpty()) {
                keyword = null;
            }
        }

        // 3. 计算偏移量
        int offset = (page - 1) * size;

        // 4. 查询列表和总数
        List<ArticleVO> list = articleMapper.findList(categoryId, keyword, offset, size);
        Long total = articleMapper.countList(categoryId, keyword);
        if (list.isEmpty()) {
            return new PageResult<>(total, list);
        }

        // 5. 批量查询标签（避免 N+1 问题）
        List<Long> articleIds = list.stream().map(ArticleVO::getId).collect(Collectors.toList());
        Map<Long, List<String>> tagMap = findTagNamesByArticleIds(articleIds);

        // 6. 组装结果：标签 + Redis 实时计数
        for (ArticleVO article : list) {
            article.setTags(tagMap.getOrDefault(article.getId(), new ArrayList<>()));
        }
        populateRedisStats(list);

        return new PageResult<>(total, list);
    }

    @Override
    public ArticleDetailVO findDetail(Long id) {
        // 1. 查询文章（不限制状态）
        ArticleDetailVO detail = articleMapper.findDetailById(id);
        if (detail == null) {
            throw new BusinessException(404, "文章不存在");
        }

        // 2. 草稿权限判断：作者本人或管理员可查看
        if (Integer.valueOf(0).equals(detail.getStatus())) {
            Long currentUserId = UserContext.getUserId();
            Long authorId = detail.getAuthor() != null ? detail.getAuthor().getId() : null;
            String currentRole = UserContext.getRole();
            if (!Objects.equals(currentUserId, authorId)
                    && !RoleConstant.ADMIN.equals(currentRole)) {
                throw new BusinessException(404, "文章不存在");
            }
            // 草稿不增加浏览量
        } else {
            // 3. 已发布文章浏览量处理（Redis 双写）
            Long viewCount = articleStatsComponent.incrementViewCount(id);
            if (viewCount != null) {
                detail.setViewCount(viewCount.intValue());
            }

            // 4. 已发布文章点赞数处理（优先 Redis）
            Long likeCount = articleStatsComponent.getLikeCount(id);
            if (likeCount != null) {
                detail.setLikeCount(likeCount.intValue());
            }

            // 5. 更新热门排行 ZSet
            if (viewCount == null) {
                viewCount = getViewCountFromRedisOrDb(id);
            }
            if (likeCount == null) {
                likeCount = detail.getLikeCount() != null ? detail.getLikeCount().longValue() : 0L;
            }
            articleStatsComponent.updateHotScore(id, viewCount, likeCount);
        }

        // 6. 查询标签
        List<Tag> tags = tagMapper.findByArticleId(id);
        List<TagVO> tagVOList = tags.stream().map(t -> {
            TagVO vo = new TagVO();
            vo.setId(t.getId());
            vo.setName(t.getName());
            return vo;
        }).collect(Collectors.toList());
        detail.setTagList(tagVOList);

        // 设置标签名称列表（兼容列表页字段）
        List<String> tagNames = tags.stream().map(Tag::getName).collect(Collectors.toList());
        detail.setTags(tagNames);

        return detail;
    }

    @Override
    @Transactional
    public void update(ArticleDTO articleDTO) {
        Long articleId = articleDTO.getId();
        if (articleId == null) {
            throw new BusinessException(400, "文章ID不能为空");
        }

        // 1. 查询文章是否存在
        Article article = articleMapper.findById(articleId);
        if (article == null) {
            throw new BusinessException(404, "文章不存在");
        }

        // 2. 权限校验
        articlePermissionComponent.checkPermission(article.getUserId());

        // 3. 参数校验
        String title = articleDTO.getTitle();
        String content = articleDTO.getContent();
        if (title != null && (title.isEmpty() || title.length() > 100)) {
            throw new BusinessException(400, "标题不能为空且长度不能超过100字符");
        }
        if (content != null && (content.isEmpty() || content.length() > 50000)) {
            throw new BusinessException(400, "正文不能为空且长度不能超过50000字符");
        }

        // 4. 校验分类是否存在（若传入）
        if (articleDTO.getCategoryId() != null) {
            Category category = categoryMapper.findById(articleDTO.getCategoryId());
            if (category == null) {
                throw new BusinessException(400, "分类不存在");
            }
        }

        // 5. 处理摘要
        String summary = articleDTO.getSummary();
        if (content != null && (summary == null || summary.isEmpty())) {
            summary = extractSummary(content);
        }

        // 6. 构建更新实体
        Article updateArticle = new Article();
        updateArticle.setId(articleId);
        if (title != null) {
            updateArticle.setTitle(title);
        }
        if (content != null) {
            updateArticle.setContent(content);
        }
        if (summary != null) {
            updateArticle.setSummary(summary);
        }
        if (articleDTO.getCover() != null) {
            updateArticle.setCover(articleDTO.getCover());
        }
        if (articleDTO.getCategoryId() != null) {
            updateArticle.setCategoryId(articleDTO.getCategoryId());
        }
        // status 仅允许 0 或 1，其他值忽略
        Integer newStatus = null;
        if (articleDTO.getStatus() != null && (articleDTO.getStatus() == 0 || articleDTO.getStatus() == 1)) {
            newStatus = articleDTO.getStatus();
            updateArticle.setStatus(newStatus);
        }

        // 7. 更新文章（update_time 在 Mapper XML 中手动设置）
        articleMapper.update(updateArticle);

        // 8. 标签关联处理（增量更新，避免全量删除再插入）
        tagRelationComponent.updateArticleTags(articleId, articleDTO.getTagIds());

        // 9. 状态变更时的 Redis 处理
        if (newStatus != null) {
            try {
                if (newStatus == 0) {
                    // 变为草稿：从 ZSet 移除
                    articleStatsComponent.removeFromHot(articleId);
                } else if (newStatus == 1 && article.getStatus() != null && article.getStatus() == 0) {
                    // 从草稿变为已发布：重新计算热度分并加入 ZSet
                    Long viewCount = getViewCountFromRedisOrDb(articleId);
                    Long likeCount = getLikeCountFromRedisOrDb(articleId);
                    articleStatsComponent.updateHotScore(articleId, viewCount, likeCount);
                }
            } catch (Exception e) {
                log.warn("状态变更时更新 ZSet 失败, articleId={}, newStatus={}", articleId, newStatus, e);
            }
        }

        // 10. 触发向量索引重建（异步）
        eventPublisher.publishEvent(new ArticlePublishedEvent(articleId, false));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        // 1. 查询文章是否存在
        Article article = articleMapper.findById(id);
        if (article == null) {
            throw new BusinessException(404, "文章不存在");
        }

        // 2. 权限校验
        articlePermissionComponent.checkPermission(article.getUserId());

        // 3. 删除标签关联
        articleTagMapper.deleteByArticleId(id);

        // 4. 删除文章
        articleMapper.deleteById(id);

        // 5. 清理 Redis
        articleStatsComponent.deleteViewCount(id);
        articleStatsComponent.deleteLikeSet(id);
        articleStatsComponent.removeFromHot(id);

        // 6. 清理向量索引
        try {
            articleIndexService.deleteIndex(id);
        } catch (Exception e) {
            log.warn("删除文章向量索引失败, articleId={}", id, e);
        }
    }

    @Override
    public PageResult<ArticleVO> findMyArticles(Integer page, Integer size) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }

        // 1. 分页参数修正
        if (page == null || page < 1) {
            page = 1;
        }
        if (size == null || size < 1) {
            size = 10;
        }
        if (size > 50) {
            size = 50;
        }

        // 2. 计算偏移量
        int offset = (page - 1) * size;

        // 3. 查询列表和总数
        List<ArticleVO> list = articleMapper.findByUserId(userId, offset, size);
        Long total = articleMapper.countByUserId(userId);
        if (list.isEmpty()) {
            return new PageResult<>(total, list);
        }

        // 4. 批量查询标签（避免 N+1 问题）
        List<Long> articleIds = list.stream().map(ArticleVO::getId).collect(Collectors.toList());
        Map<Long, List<String>> tagMap = findTagNamesByArticleIds(articleIds);

        // 5. 组装结果：标签 + Redis 实时计数
        for (ArticleVO article : list) {
            article.setTags(tagMap.getOrDefault(article.getId(), new ArrayList<>()));
        }
        populateRedisStats(list);

        return new PageResult<>(total, list);
    }

    @Override
    public void like(Long articleId) {
        // 1. 参数校验
        if (articleId == null || articleId <= 0) {
            throw new BusinessException(400, "文章ID不能为空");
        }

        // 2. 校验文章
        Article article = articleMapper.findById(articleId);
        if (article == null) {
            throw new BusinessException(404, "文章不存在");
        }
        if (article.getStatus() == null || article.getStatus() != 1) {
            throw new BusinessException(400, "草稿文章禁止操作");
        }

        // 3. 获取用户ID
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }

        // 4. Redis 点赞（原子操作，防重复点赞）
        if (!articleStatsComponent.like(articleId, userId)) {
            throw new BusinessException(409, "已点赞");
        }

        // 5. 更新 MySQL like_count（失败不影响 Redis 结果，后续定时任务补偿）
        try {
            articleMapper.update(new Article() {{
                setId(articleId);
                setLikeCount((article.getLikeCount() != null ? article.getLikeCount() : 0L) + 1);
            }});
        } catch (Exception e) {
            log.warn("点赞后更新 MySQL like_count 失败, articleId={}, 将由定时任务补偿", articleId, e);
        }

        // 6. 更新热门排行 ZSet
        Long viewCount = getViewCountFromRedisOrDb(articleId);
        Long likeCount = articleStatsComponent.getLikeCount(articleId);
        if (likeCount == null) {
            likeCount = getLikeCountFromRedisOrDb(articleId);
        }
        articleStatsComponent.updateHotScore(articleId, viewCount, likeCount);
    }

    @Override
    public void unlike(Long articleId) {
        // 1. 参数校验
        if (articleId == null || articleId <= 0) {
            throw new BusinessException(400, "文章ID不能为空");
        }

        // 2. 获取用户ID
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }

        // 3. Redis 取消点赞
        if (!articleStatsComponent.unlike(articleId, userId)) {
            throw new BusinessException(404, "未点赞");
        }

        // 4. 更新 MySQL like_count（失败不影响 Redis 结果，后续定时任务补偿）
        Article article = articleMapper.findById(articleId);
        if (article != null && article.getLikeCount() != null && article.getLikeCount() > 0) {
            try {
                articleMapper.update(new Article() {{
                    setId(articleId);
                    setLikeCount(article.getLikeCount() - 1);
                }});
            } catch (Exception e) {
                log.warn("取消点赞后更新 MySQL like_count 失败, articleId={}, 将由定时任务补偿", articleId, e);
            }
        }

        // 5. 更新热门排行 ZSet
        Long viewCount = getViewCountFromRedisOrDb(articleId);
        Long likeCount = articleStatsComponent.getLikeCount(articleId);
        if (likeCount == null || likeCount == 0) {
            // 点赞数为 0 且浏览量也为 0，从 ZSet 移除
            if (viewCount == 0) {
                articleStatsComponent.removeFromHot(articleId);
            } else {
                articleStatsComponent.updateHotScore(articleId, viewCount, 0L);
            }
        } else {
            articleStatsComponent.updateHotScore(articleId, viewCount, likeCount);
        }
    }

    @Override
    public LikeStatusVO getLikeStatus(Long articleId) {
        // 1. 参数校验
        if (articleId == null || articleId <= 0) {
            throw new BusinessException(400, "文章ID不能为空");
        }

        // 2. 校验文章
        Article article = articleMapper.findById(articleId);
        if (article == null) {
            throw new BusinessException(404, "文章不存在");
        }
        if (article.getStatus() == null || article.getStatus() != 1) {
            throw new BusinessException(400, "草稿文章禁止操作");
        }

        // 3. 获取用户ID
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }

        LikeStatusVO vo = new LikeStatusVO();

        // 4. 查询是否点赞
        vo.setLiked(articleStatsComponent.isLiked(articleId, userId));

        // 5. 查询点赞总数
        Long likeCount = articleStatsComponent.getLikeCount(articleId);
        if (likeCount != null) {
            vo.setLikeCount(likeCount);
        } else {
            vo.setLikeCount(article.getLikeCount() != null ? article.getLikeCount().longValue() : 0L);
        }

        return vo;
    }

    @Override
    public PageResult<ArticleVO> findAdminList(Integer page, Integer size, String keyword) {
        // 1. 分页参数修正
        if (page == null || page < 1) {
            page = 1;
        }
        if (size == null || size < 1) {
            size = 10;
        }
        if (size > 50) {
            size = 50;
        }

        // 2. 处理关键词：去除首尾空格，空字符串视为未输入
        if (keyword != null) {
            keyword = keyword.trim();
            if (keyword.isEmpty()) {
                keyword = null;
            }
        }

        int offset = (page - 1) * size;

        // 3. 查询列表和总数
        List<ArticleVO> list = articleMapper.findAdminList(keyword, offset, size);
        Long total = articleMapper.countAdminList(keyword);
        if (list.isEmpty()) {
            return new PageResult<>(total, list);
        }

        // 4. 批量查询标签（避免 N+1 问题）
        List<Long> articleIds = list.stream().map(ArticleVO::getId).collect(Collectors.toList());
        Map<Long, List<String>> tagMap = findTagNamesByArticleIds(articleIds);

        // 5. 组装结果：标签 + Redis 实时计数
        for (ArticleVO article : list) {
            article.setTags(tagMap.getOrDefault(article.getId(), new ArrayList<>()));
        }
        populateRedisStats(list);

        return new PageResult<>(total, list);
    }

    @Override
    public List<HotArticleVO> findHotArticles(Integer size) {
        // 1. 参数修正
        if (size == null || size < 1) {
            size = 10;
        }
        if (size > 50) {
            size = 50;
        }

        // 2. 查询 ZSet
        Set<ZSetOperations.TypedTuple<String>> tuples;
        try {
            tuples = redisUtil.zRevRangeWithScores(RedisKeyConstant.ARTICLE_HOT_ZSET, 0, size - 1);
        } catch (Exception e) {
            log.warn("查询热门排行 ZSet 失败", e);
            return new ArrayList<>();
        }

        if (tuples == null || tuples.isEmpty()) {
            return new ArrayList<>();
        }

        // 3. 提取 articleId 和 hotScore
        List<Long> articleIds = new ArrayList<>();
        java.util.Map<Long, Double> scoreMap = new java.util.HashMap<>();
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            String articleIdStr = tuple.getValue();
            Double score = tuple.getScore();
            if (articleIdStr != null) {
                try {
                    Long articleId = Long.parseLong(articleIdStr);
                    articleIds.add(articleId);
                    if (score != null) {
                        scoreMap.put(articleId, score);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }

        if (articleIds.isEmpty()) {
            return new ArrayList<>();
        }

        // 4. 批量查询文章详情
        List<ArticleVO> articleList = articleMapper.findByIds(articleIds);

        // 5. 组装 HotArticleVO（过滤已删除/变草稿的文章并清理 ZSet）
        List<HotArticleVO> hotList = new ArrayList<>();
        for (ArticleVO articleVO : articleList) {
            if (articleVO == null || articleVO.getStatus() == null || articleVO.getStatus() != 1) {
                // 文章已删除或变草稿，从 ZSet 移除
                articleStatsComponent.removeFromHot(articleVO != null ? articleVO.getId() : null);
                continue;
            }

            HotArticleVO hotVO = new HotArticleVO();
            hotVO.setId(articleVO.getId());
            hotVO.setTitle(articleVO.getTitle());
            hotVO.setSummary(articleVO.getSummary());
            hotVO.setCover(articleVO.getCover());
            hotVO.setAuthor(articleVO.getAuthor());
            hotVO.setCategory(articleVO.getCategory());
            hotVO.setCreateTime(articleVO.getCreateTime());
            hotVO.setStatus(articleVO.getStatus());

            // 从 Redis 读取实时计数
            Long articleId = articleVO.getId();
            Long viewCount = articleStatsComponent.getViewCount(articleId);
            hotVO.setViewCount(viewCount != null ? viewCount.intValue() : articleVO.getViewCount());

            Long likeCount = articleStatsComponent.getLikeCount(articleId);
            hotVO.setLikeCount(likeCount != null ? likeCount.intValue() : articleVO.getLikeCount());

            // 设置热度分
            Double hotScore = scoreMap.get(articleId);
            if (hotScore != null) {
                hotVO.setHotScore(hotScore);
            } else {
                hotVO.setHotScore(articleStatsComponent.calculateHotScore(
                        hotVO.getViewCount() != null ? hotVO.getViewCount().longValue() : 0L,
                        hotVO.getLikeCount() != null ? hotVO.getLikeCount().longValue() : 0L
                ));
            }

            hotList.add(hotVO);
        }

        // 批量查询标签（避免 N+1 问题）
        List<Long> allArticleIds = hotList.stream().map(HotArticleVO::getId).collect(Collectors.toList());
        Map<Long, List<String>> tagMap = findTagNamesByArticleIds(allArticleIds);
        for (HotArticleVO hotVO : hotList) {
            hotVO.setTags(tagMap.getOrDefault(hotVO.getId(), new ArrayList<>()));
        }

        return hotList;
    }

    /**
     * 为文章列表填充 Redis 实时浏览量与点赞数
     *
     * @param list 文章列表
     */
    private void populateRedisStats(List<? extends ArticleVO> list) {
        for (ArticleVO article : list) {
            Long articleId = article.getId();

            Long viewCount = articleStatsComponent.getViewCount(articleId);
            if (viewCount != null) {
                article.setViewCount(viewCount.intValue());
            }

            Long likeCount = articleStatsComponent.getLikeCount(articleId);
            if (likeCount != null) {
                article.setLikeCount(likeCount.intValue());
            }
        }
    }

    /**
     * 从 Redis 或 MySQL 获取浏览量（Redis 优先）
     */
    private Long getViewCountFromRedisOrDb(Long articleId) {
        Long viewCount = articleStatsComponent.getViewCount(articleId);
        if (viewCount != null) {
            return viewCount;
        }
        Article article = articleMapper.findById(articleId);
        return article != null && article.getViewCount() != null ? article.getViewCount().longValue() : 0L;
    }

    /**
     * 从 Redis 或 MySQL 获取点赞数（Redis 优先）
     */
    private Long getLikeCountFromRedisOrDb(Long articleId) {
        Long likeCount = articleStatsComponent.getLikeCount(articleId);
        if (likeCount != null) {
            return likeCount;
        }
        Article article = articleMapper.findById(articleId);
        return article != null && article.getLikeCount() != null ? article.getLikeCount().longValue() : 0L;
    }

    /**
     * 从正文提取摘要
     *
     * @param content 正文
     * @return 摘要
     */
    private String extractSummary(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        // 去除Markdown标记：行内代码、代码块、标题标记、链接、图片等
        String plainText = content
                .replaceAll("```[\\s\\S]*?```", "")     // 代码块
                .replaceAll("`([^`]+)`", "$1")           // 行内代码
                .replaceAll("!\\[.*?\\]\\(.*?\\)", "")   // 图片
                .replaceAll("\\[([^\\]]+)\\]\\(.*?\\)", "$1") // 链接
                .replaceAll("#{1,6}\\s*", "")            // 标题标记
                .replaceAll("\\*+|_+|~+|`+", "")        // 粗体、斜体、删除线
                .replaceAll("\\n+", " ")                 // 换行转空格
                .trim();

        if (plainText.length() <= SUMMARY_MAX_LENGTH) {
            return plainText;
        }
        return plainText.substring(0, SUMMARY_MAX_LENGTH);
    }

    /**
     * 根据文章ID查询标签名称列表
     *
     * @param articleId 文章ID
     * @return 标签名称列表
     */
    private List<String> findTagNamesByArticleId(Long articleId) {
        List<Tag> tags = tagMapper.findByArticleId(articleId);
        return tags.stream().map(Tag::getName).collect(Collectors.toList());
    }

    /**
     * 批量查询文章标签（替代循环查询，解决 N+1 问题）
     *
     * @param articleIds 文章ID列表
     * @return Map<文章ID, 标签名称列表>
     */
    private Map<Long, List<String>> findTagNamesByArticleIds(List<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return new HashMap<>();
        }
        List<ArticleTagDTO> relations = tagMapper.findRelationsByArticleIds(articleIds);
        Map<Long, List<String>> result = new HashMap<>();
        for (ArticleTagDTO relation : relations) {
            result.computeIfAbsent(relation.getArticleId(), k -> new ArrayList<>()).add(relation.getTagName());
        }
        return result;
    }
}
