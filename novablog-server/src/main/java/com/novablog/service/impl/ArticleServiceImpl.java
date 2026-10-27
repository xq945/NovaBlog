package com.novablog.service.impl;

import com.novablog.common.annotation.AutoFillTime;
import com.novablog.common.enums.OperationType;
import com.novablog.common.PageResult;
import com.novablog.common.UserContext;
import com.novablog.common.constant.RedisKeyConstant;
import com.novablog.common.constant.RoleConstant;
import com.novablog.common.exception.BusinessException;
import com.novablog.dto.request.ArticleDTO;
import com.novablog.dto.request.ArticleTagDTO;
import com.novablog.entity.Article;
import com.novablog.entity.Category;
import com.novablog.entity.Tag;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.novablog.mapper.ArticleLikeMapper;
import com.novablog.mapper.ArticleMapper;
import com.novablog.mapper.ArticleTagMapper;
import com.novablog.mapper.CategoryMapper;
import com.novablog.mapper.TagMapper;
import com.novablog.service.ArticleService;
import com.novablog.service.NotificationService;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Comparator;
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
public class ArticleServiceImpl extends ServiceImpl<ArticleMapper, Article> implements ArticleService {

    private final ArticleMapper articleMapper;
    private final ArticleLikeMapper articleLikeMapper;
    private final ArticleTagMapper articleTagMapper;
    private final TagMapper tagMapper;
    private final CategoryMapper categoryMapper;
    private final RedisUtil redisUtil;
    private final ArticleStatsComponent articleStatsComponent;
    private final TagRelationComponent tagRelationComponent;
    private final ArticlePermissionComponent articlePermissionComponent;
    private final ApplicationEventPublisher eventPublisher;
    private final ArticleIndexService articleIndexService;
    private final NotificationService notificationService;

    /**
     * 摘要自动截取长度
     */
    private static final int SUMMARY_MAX_LENGTH = 150;

    @Override
    @Transactional
    @AutoFillTime(OperationType.INSERT)
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
                // 热度分随浏览量增量累加，避免并发请求用旧快照覆盖更大分数
                articleStatsComponent.addViewToHotScore(id);
            }

            // 4. 已发布文章点赞数处理（优先 Redis）
            Long likeCount = articleStatsComponent.getLikeCount(id);
            if (likeCount != null) {
                detail.setLikeCount(likeCount.intValue());
            }
        }

        // 5. 查询标签
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
    @AutoFillTime(OperationType.UPDATE)
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

        // 9. 状态变更时的 Redis 处理放在事务提交后，
        //    事务回滚时榜单不应留下"已发布/已下架"的痕迹
        if (newStatus != null) {
            final Integer targetStatus = newStatus;
            afterCommit(() -> {
                if (targetStatus == 0) {
                    // 变为草稿：从 ZSet 移除
                    articleStatsComponent.removeFromHot(articleId);
                } else if (targetStatus == 1
                        && article.getStatus() != null && article.getStatus() == 0) {
                    // 从草稿变为已发布：按当前计数重算并加入 ZSet
                    Long viewCount = getViewCountFromRedisOrDb(articleId);
                    Long likeCount = getLikeCountFromRedisOrDb(articleId);
                    articleStatsComponent.updateHotScore(articleId, viewCount, likeCount);
                }
            });
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

        // 5. 清理 Redis，放在事务提交后，避免回滚时缓存已被删除
        afterCommit(() -> {
            articleStatsComponent.deleteViewCount(id);
            articleStatsComponent.deleteLikeSet(id);
            articleStatsComponent.removeFromHot(id);
        });

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

        // 4. 点赞关系落库。数据库是正本，唯一键保证幂等；
        //    Redis 只是缓存，写失败不影响已经落库的点赞
        int inserted = articleLikeMapper.insertIgnore(userId, articleId);
        if (inserted == 0) {
            // 已点赞。顺手补一次缓存：Redis 丢数据后靠这次操作自愈
            articleStatsComponent.cacheLike(articleId, userId);
            throw new BusinessException(409, "已点赞");
        }
        articleStatsComponent.cacheLike(articleId, userId);

        // 5. 更新 MySQL like_count（原子自增，失败由定时任务按关系表校准）
        try {
            articleMapper.incrementLikeCount(articleId);
        } catch (Exception e) {
            log.warn("点赞后更新 MySQL like_count 失败, articleId={}, 将由定时任务校准", articleId, e);
        }

        // 6. 热度分 +3
        articleStatsComponent.addLikeToHotScore(articleId);

        // 7. 通知文章作者。自己赞自己不通知，通知失败只记日志，不影响点赞结果
        notificationService.notifyLike(article.getUserId(), userId, articleId, article.getTitle());
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

        // 3. 删除点赞关系（数据库是正本）；删不到说明本来就没点赞
        int deleted = articleLikeMapper.deleteByUserAndArticle(userId, articleId);
        if (deleted == 0) {
            // 缓存与数据库不一致时，顺手清掉缓存里的残留
            articleStatsComponent.cacheUnlike(articleId, userId);
            throw new BusinessException(404, "未点赞");
        }
        articleStatsComponent.cacheUnlike(articleId, userId);

        // 4. 更新 MySQL like_count（原子自减，失败由定时任务按关系表校准）
        try {
            articleMapper.decrementLikeCount(articleId);
        } catch (Exception e) {
            log.warn("取消点赞后更新 MySQL like_count 失败, articleId={}, 将由定时任务校准", articleId, e);
        }

        // 5. 热度分 -3；分数归零说明既无浏览也无点赞，从榜单移除
        articleStatsComponent.removeLikeFromHotScore(articleId);
        Double hotScore = articleStatsComponent.getHotScore(articleId);
        if (hotScore != null && hotScore <= 0) {
            articleStatsComponent.removeFromHot(articleId);
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

        // 4. 查询是否点赞：缓存不可用时回落关系表
        Boolean liked = articleStatsComponent.isLiked(articleId, userId);
        if (liked == null) {
            liked = articleLikeMapper.exists(userId, articleId) > 0;
        }
        vo.setLiked(liked);

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
        int safeSize = size == null || size < 1 ? 10 : Math.min(size, 50);

        // 2. 优先走 ZSet；Redis 不可用时按数据库计数兜底，而不是返回空榜
        List<HotArticleVO> hotList;
        List<Long> ghostIds = new ArrayList<>();
        try {
            hotList = loadHotFromZSet(safeSize, ghostIds);
        } catch (Exception e) {
            log.warn("查询热门排行失败，降级为数据库排序", e);
            ghostIds.clear();
            hotList = loadHotFromDb(safeSize);
        }

        // 3. 幽灵成员在读取结束后统一清理：边取边删会让 ZSet 前移，导致分页漏掉成员
        for (Long ghostId : ghostIds) {
            articleStatsComponent.removeFromHot(ghostId);
        }

        // 4. 统一按热度分降序，两条数据来源的顺序都收敛到同一口径
        sortByHotScore(hotList);
        fillTags(hotList);
        return hotList;
    }

    /**
     * 从 ZSet 按排名分批取，跳过已下架/已删除文章占用的名额，直到凑满或取尽
     */
    private List<HotArticleVO> loadHotFromZSet(int size, List<Long> ghostIds) {
        List<HotArticleVO> result = new ArrayList<>();
        long offset = 0;
        while (result.size() < size) {
            Set<ZSetOperations.TypedTuple<String>> tuples = redisUtil.zRevRangeWithScores(
                RedisKeyConstant.ARTICLE_HOT_ZSET, offset, offset + size - 1);
            if (tuples == null || tuples.isEmpty()) {
                break;
            }
            // 本批不足 size 条说明 ZSet 已经取尽，本轮之后不必再查
            boolean exhausted = tuples.size() < size;

            List<Long> batchIds = new ArrayList<>();
            Map<Long, Double> scoreMap = new HashMap<>();
            for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                String articleIdStr = tuple.getValue();
                if (articleIdStr == null) {
                    continue;
                }
                try {
                    Long articleId = Long.parseLong(articleIdStr);
                    batchIds.add(articleId);
                    scoreMap.put(articleId, tuple.getScore());
                } catch (NumberFormatException ignored) {
                }
            }
            if (batchIds.isEmpty()) {
                if (exhausted) {
                    break;
                }
                offset += size;
                continue;
            }

            List<ArticleVO> articleList = articleMapper.findByIds(batchIds);
            if (articleList == null) {
                articleList = new ArrayList<>();
            }

            // SQL 带 status = 1，查不回来的 ID 就是幽灵成员
            Set<Long> fetchedIds = articleList.stream()
                .map(ArticleVO::getId)
                .collect(Collectors.toSet());
            for (Long articleId : batchIds) {
                if (!fetchedIds.contains(articleId)) {
                    ghostIds.add(articleId);
                }
            }

            // 批量读实时计数：浏览量一次 multiGet，点赞数一次 pipeline
            Map<Long, Long> viewCounts = batchViewCounts(batchIds);
            Map<Long, Long> likeCounts = batchLikeCounts(batchIds);
            for (ArticleVO articleVO : articleList) {
                if (result.size() >= size) {
                    break;
                }
                result.add(buildHotArticle(articleVO, scoreMap.get(articleVO.getId()),
                    viewCounts, likeCounts));
            }

            offset += size;
            if (exhausted) {
                break;
            }
        }
        return result;
    }

    /**
     * Redis 不可用时的兜底：按数据库里的浏览量与点赞数排序
     */
    private List<HotArticleVO> loadHotFromDb(int size) {
        List<ArticleVO> articleList;
        try {
            articleList = articleMapper.findHotFromDb(size);
        } catch (Exception e) {
            log.warn("热门文章数据库兜底查询失败", e);
            return new ArrayList<>();
        }
        if (articleList == null || articleList.isEmpty()) {
            return new ArrayList<>();
        }
        List<HotArticleVO> hotList = new ArrayList<>(articleList.size());
        for (ArticleVO articleVO : articleList) {
            hotList.add(buildHotArticle(articleVO, null, null, null));
        }
        return hotList;
    }

    /**
     * 批量读取浏览量，返回的 Map 只包含 Redis 中存在的文章
     */
    private Map<Long, Long> batchViewCounts(List<Long> articleIds) {
        List<String> keys = articleIds.stream()
            .map(RedisKeyConstant::articleView)
            .collect(Collectors.toList());
        List<String> values = redisUtil.multiGet(keys);
        Map<Long, Long> counts = new HashMap<>();
        if (values == null) {
            return counts;
        }
        for (int i = 0; i < articleIds.size() && i < values.size(); i++) {
            String value = values.get(i);
            if (value == null) {
                continue;
            }
            try {
                counts.put(articleIds.get(i), Long.parseLong(value));
            } catch (NumberFormatException ignored) {
            }
        }
        return counts;
    }

    /**
     * 批量读取点赞数，返回的 Map 只包含 Redis 中存在的文章
     */
    private Map<Long, Long> batchLikeCounts(List<Long> articleIds) {
        List<String> keys = articleIds.stream()
            .map(RedisKeyConstant::articleLike)
            .collect(Collectors.toList());
        List<Long> cards = redisUtil.multiSCard(keys);
        Map<Long, Long> counts = new HashMap<>();
        for (int i = 0; i < articleIds.size() && i < cards.size(); i++) {
            Long card = cards.get(i);
            // SCARD 对不存在的键也返回 0；0 值不放入 Map，
            // 交由调用方回落到数据库计数，避免缓存缺失时把历史点赞数显示成 0
            if (card != null && card > 0) {
                counts.put(articleIds.get(i), card);
            }
        }
        return counts;
    }

    /**
     * 组装榜单条目：Redis 计数缺失时回落到数据库值
     *
     * @param zsetScore ZSet 中的分数；为空时按计数重新计算（数据库兜底路径）
     */
    private HotArticleVO buildHotArticle(ArticleVO articleVO, Double zsetScore,
                                          Map<Long, Long> viewCounts,
                                          Map<Long, Long> likeCounts) {
        HotArticleVO hotVO = new HotArticleVO();
        hotVO.setId(articleVO.getId());
        hotVO.setTitle(articleVO.getTitle());
        hotVO.setSummary(articleVO.getSummary());
        hotVO.setCover(articleVO.getCover());
        hotVO.setAuthor(articleVO.getAuthor());
        hotVO.setCategory(articleVO.getCategory());
        hotVO.setCreateTime(articleVO.getCreateTime());
        hotVO.setStatus(articleVO.getStatus());

        Long articleId = articleVO.getId();
        Long viewCount = viewCounts == null ? null : viewCounts.get(articleId);
        Long likeCount = likeCounts == null ? null : likeCounts.get(articleId);
        hotVO.setViewCount(viewCount != null ? viewCount.intValue() : articleVO.getViewCount());
        hotVO.setLikeCount(likeCount != null ? likeCount.intValue() : articleVO.getLikeCount());

        if (zsetScore != null) {
            hotVO.setHotScore(zsetScore);
        } else {
            long view = viewCount != null ? viewCount
                : (articleVO.getViewCount() == null ? 0L : articleVO.getViewCount().longValue());
            long like = likeCount != null ? likeCount
                : (articleVO.getLikeCount() == null ? 0L : articleVO.getLikeCount().longValue());
            hotVO.setHotScore(articleStatsComponent.calculateHotScore(view, like));
        }
        return hotVO;
    }

    private void sortByHotScore(List<HotArticleVO> hotList) {
        hotList.sort(Comparator.comparingDouble(
            (HotArticleVO vo) -> vo.getHotScore() == null ? 0d : vo.getHotScore()).reversed());
    }

    private void fillTags(List<? extends ArticleVO> list) {
        if (list.isEmpty()) {
            return;
        }
        List<Long> articleIds = list.stream().map(ArticleVO::getId).collect(Collectors.toList());
        Map<Long, List<String>> tagMap = findTagNamesByArticleIds(articleIds);
        for (ArticleVO articleVO : list) {
            articleVO.setTags(tagMap.getOrDefault(articleVO.getId(), new ArrayList<>()));
        }
    }

    /**
     * 在事务提交后执行缓存操作；无事务时立即执行
     *
     * <p>Redis 不参与数据库事务，先写缓存再回滚会留下不一致，
     * 因此热榜变更与统计清理统一推迟到提交之后。
     */
    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        action.run();
                    } catch (Exception e) {
                        log.warn("事务提交后更新缓存失败", e);
                    }
                }
            });
            return;
        }
        try {
            action.run();
        } catch (Exception e) {
            log.warn("更新缓存失败", e);
        }
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
